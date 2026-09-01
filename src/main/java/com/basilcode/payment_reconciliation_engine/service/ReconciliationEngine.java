package com.basilcode.payment_reconciliation_engine.service;

import com.basilcode.payment_reconciliation_engine.entity.ReconciliationItem;
import com.basilcode.payment_reconciliation_engine.entity.ReconciliationItem.ReconciliationReason;
import com.basilcode.payment_reconciliation_engine.entity.ReconciliationItem.ReconciliationStatus;
import com.basilcode.payment_reconciliation_engine.entity.SettlementRecord;
import com.basilcode.payment_reconciliation_engine.entity.Transaction;
import com.basilcode.payment_reconciliation_engine.repositories.ReconciliationRepository;
import com.basilcode.payment_reconciliation_engine.repositories.SettlementRepository;
import com.basilcode.payment_reconciliation_engine.repositories.TransactionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;


@Service
@Slf4j
public class ReconciliationEngine {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_RECONCILED = "RECONCILED";
    public static final String STATUS_MISMATCHED = "MISMATCHED";
    public static final String STATUS_ORPHAN = "ORPHAN";

    private static final Set<String> TX_SUCCEEDED = Set.of("success", "successful", "completed", "settled");
    private static final Set<String> TX_FAILED = Set.of("failed", "abandoned", "reversed");

    private final TransactionRepository transactionRepository;
    private final SettlementRepository settlementRepository;
    private final ReconciliationRepository reconciliationRepository;
    private final ReconciliationEngine self;

    public ReconciliationEngine(TransactionRepository transactionRepository,
                                SettlementRepository settlementRepository,
                                ReconciliationRepository reconciliationRepository,
                                @Lazy ReconciliationEngine self) {
        this.transactionRepository = transactionRepository;
        this.settlementRepository = settlementRepository;
        this.reconciliationRepository = reconciliationRepository;
        this.self = self;
    }

    @CacheEvict(cacheNames = "reconciliationSummary", allEntries = true)
    public PassResult reconciliation() {
        Instant startedAt = Instant.now();
        PassResult.Builder result = PassResult.builder();
        List<Transaction> candidates =
                transactionRepository.findByReconciliationStatusIn(List.of(STATUS_PENDING, STATUS_ORPHAN));
        log.info("Reconciliation pass starting: {} transaction(s) to examine", candidates.size());

        for (Transaction candidate : candidates) {
            try {
                result.record(self.reconcileTransaction(candidate.getId()));
            } catch (Exception e) {
                result.failure();
                log.error("Failed to reconcile transaction {}: {}", candidate.getId(), e.getMessage(), e);
            }
        }

        List<SettlementRecord> unmatched = settlementRepository.findWithoutMatchingTransaction();
        log.info("Reconciliation pass: {} settlement(s) with no matching transaction", unmatched.size());

        for (SettlementRecord settlement : unmatched) {
            try {
                if (self.reconcileSettlement(settlement.getId())) {
                    result.missingWebhook();
                }
            } catch (Exception e) {
                result.failure();
                log.error("Failed to reconcile settlement {}: {}", settlement.getId(), e.getMessage(), e);
            }
        }

        PassResult built = result.build(candidates.size(), unmatched.size(), startedAt);
        log.info("Reconciliation pass complete: {}", built);
        return built;
    }


    @Transactional
    public ReconciliationReason reconcileTransaction(UUID transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId).orElse(null);
        if (transaction == null) {
            return null;
        }

        Optional<SettlementRecord> settlementOpt = settlementRepository
                .findByProviderAndProviderReference(transaction.getProvider(), transaction.getProviderReference());

        if (settlementOpt.isEmpty()) {
            return apply(transaction, null, ReconciliationReason.ORPHAN_TRANSACTION, STATUS_ORPHAN);
        }

        SettlementRecord settlement = settlementOpt.get();

        if (!statusesAgree(transaction.getStatus(), settlement.getStatus())) {
            log.warn("Status mismatch for {}: transaction={} settlement={}",
                    transaction.getProviderReference(), transaction.getStatus(), settlement.getStatus());
            return apply(transaction, settlement, ReconciliationReason.STATUS_MISMATCH, STATUS_MISMATCHED);
        }

        if (!amountsMatch(transaction.getAmount(), settlement.getAmount())) {
            log.warn("Amount mismatch for {}: transaction={} settlement={}",
                    transaction.getProviderReference(), transaction.getAmount(), settlement.getAmount());
            return apply(transaction, settlement, ReconciliationReason.AMOUNT_MISMATCH, STATUS_MISMATCHED);
        }

        log.info("Reconciled {} successfully", transaction.getProviderReference());
        return apply(transaction, settlement, ReconciliationReason.MATCHED, STATUS_RECONCILED);
    }


    @Transactional
    public boolean reconcileSettlement(UUID settlementId) {
        SettlementRecord settlement = settlementRepository.findById(settlementId).orElse(null);
        if (settlement == null) {
            return false;
        }

        boolean transactionExists = transactionRepository
                .findByProviderAndProviderReference(settlement.getProvider(), settlement.getProviderReference())
                .isPresent();
        if (transactionExists) {
            return false;
        }

        boolean alreadyRaised = reconciliationRepository.findBySettlementRecordId(settlement.getId()).stream()
                .anyMatch(i -> i.getReason() == ReconciliationReason.MISSING_WEBHOOK);
        if (alreadyRaised) {
            return false;
        }

        log.warn("Settlement {} has no matching transaction", settlement.getProviderReference());

        ReconciliationItem item = new ReconciliationItem();
        item.setTransaction(null);
        item.setSettlementRecord(settlement);
        item.setReason(ReconciliationReason.MISSING_WEBHOOK);
        item.setStatus(ReconciliationStatus.OPEN);
        item.setCreatedAt(Instant.now());
        reconciliationRepository.save(item);
        return true;
    }


    private ReconciliationReason apply(Transaction transaction,
                                       SettlementRecord settlement,
                                       ReconciliationReason reason,
                                       String transactionStatus) {
        transaction.setReconciliationStatus(transactionStatus);
        transactionRepository.save(transaction);

        List<ReconciliationItem> existing = reconciliationRepository.findByTransactionId(transaction.getId());

        for (ReconciliationItem stale : existing) {
            if (stale.getReason() != reason && stale.getStatus() != ReconciliationStatus.RESOLVED) {
                stale.setStatus(ReconciliationStatus.RESOLVED);
                stale.setResolvedAt(Instant.now());
                stale.setNotes(appendNote(stale.getNotes(), "Superseded by " + reason.name() + "."));
                reconciliationRepository.save(stale);
                log.info("Resolved stale {} item for {}", stale.getReason(), transaction.getProviderReference());
            }
        }

        boolean alreadyRaised = existing.stream().anyMatch(i -> i.getReason() == reason);
        if (alreadyRaised) {
            return reason;
        }

        ReconciliationItem item = new ReconciliationItem();
        item.setTransaction(transaction);
        item.setSettlementRecord(settlement);
        item.setReason(reason);
        item.setStatus(reason == ReconciliationReason.MATCHED
                ? ReconciliationStatus.RESOLVED
                : ReconciliationStatus.OPEN);
        item.setCreatedAt(Instant.now());
        if (reason == ReconciliationReason.MATCHED) {
            item.setResolvedAt(Instant.now());
        }
        reconciliationRepository.save(item);
        return reason;
    }

    private static String appendNote(String existing, String addition) {
        if (existing == null || existing.isBlank()) {
            return addition;
        }
        return existing + " " + addition;
    }

    private static boolean amountsMatch(BigDecimal transactionAmount, BigDecimal settlementAmount) {
        if (transactionAmount == null || settlementAmount == null) {
            return false;
        }
        return transactionAmount.compareTo(settlementAmount) == 0;
    }


    private static boolean statusesAgree(String transactionStatus, String settlementStatus) {
        if (transactionStatus == null || settlementStatus == null) {
            return true; 
        }
        String tx = transactionStatus.trim().toLowerCase();
        String settlement = settlementStatus.trim().toLowerCase();

        if (TX_SUCCEEDED.contains(tx)) {
            return "settled".equals(settlement);
        }
        if (TX_FAILED.contains(tx)) {
            return "failed".equals(settlement) || "reversed".equals(settlement);
        }
        return true; 
    }

    public record PassResult(int transactionsScanned,
                             int settlementsScanned,
                             int matched,
                             int amountMismatches,
                             int statusMismatches,
                             int orphans,
                             int missingWebhooks,
                             int failures,
                             long durationMillis) {

        static Builder builder() {
            return new Builder();
        }

        static final class Builder {
            private int matched;
            private int amountMismatches;
            private int statusMismatches;
            private int orphans;
            private int missingWebhooks;
            private int failures;

            void record(ReconciliationReason reason) {
                if (reason == null) {
                    return;
                }
                switch (reason) {
                    case MATCHED -> matched++;
                    case AMOUNT_MISMATCH -> amountMismatches++;
                    case STATUS_MISMATCH -> statusMismatches++;
                    case ORPHAN_TRANSACTION -> orphans++;
                    case MISSING_WEBHOOK -> missingWebhooks++;
                }
            }

            void missingWebhook() {
                missingWebhooks++;
            }

            void failure() {
                failures++;
            }

            PassResult build(int transactionsScanned, int settlementsScanned, Instant startedAt) {
                return new PassResult(transactionsScanned, settlementsScanned, matched,
                        amountMismatches, statusMismatches, orphans, missingWebhooks, failures,
                        Instant.now().toEpochMilli() - startedAt.toEpochMilli());
            }
        }
    }
}
