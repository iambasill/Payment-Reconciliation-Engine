package com.basilcode.payment_reconciliation_engine.service;

import com.basilcode.payment_reconciliation_engine.dto.PageResponse;
import com.basilcode.payment_reconciliation_engine.dto.ReconciliationItemResponse;
import com.basilcode.payment_reconciliation_engine.dto.ReconciliationSummaryResponse;
import com.basilcode.payment_reconciliation_engine.dto.UpdateReconciliationItemRequest;
import com.basilcode.payment_reconciliation_engine.entity.ReconciliationItem;
import com.basilcode.payment_reconciliation_engine.entity.ReconciliationItem.ReconciliationReason;
import com.basilcode.payment_reconciliation_engine.entity.ReconciliationItem.ReconciliationStatus;
import com.basilcode.payment_reconciliation_engine.entity.SettlementRecord;
import com.basilcode.payment_reconciliation_engine.entity.Transaction;
import com.basilcode.payment_reconciliation_engine.repositories.ReconciliationRepository;
import com.basilcode.payment_reconciliation_engine.repositories.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Read and review side of reconciliation: lists the queue, exposes one item,
 * and moves items through OPEN -> INVESTIGATING -> RESOLVED.
 *
 * <p>Separate from {@link ReconciliationEngine}, which produces items. This
 * service only ever consumes and annotates them.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ReconciliationReviewService {

    private static final int MAX_PAGE_SIZE = 200;

    /** Which statuses a reviewer may move to from each current status. */
    private static final Map<ReconciliationStatus, Set<ReconciliationStatus>> ALLOWED_TRANSITIONS =
            Map.of(
                    ReconciliationStatus.OPEN,
                    Set.of(ReconciliationStatus.INVESTIGATING, ReconciliationStatus.RESOLVED),
                    ReconciliationStatus.INVESTIGATING,
                    Set.of(ReconciliationStatus.OPEN, ReconciliationStatus.RESOLVED),
                    ReconciliationStatus.RESOLVED,
                    Set.of(ReconciliationStatus.OPEN, ReconciliationStatus.INVESTIGATING)
            );

    private final ReconciliationRepository reconciliationRepository;
    private final TransactionRepository transactionRepository;

    @Transactional(readOnly = true)
    public PageResponse<ReconciliationItemResponse> listItems(String statusFilter,
                                                             String reasonFilter,
                                                             int page,
                                                             int size) {
        ReconciliationStatus status = parseStatus(statusFilter);
        ReconciliationReason reason = parseReason(reasonFilter);
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.clamp(size, 1, MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<ReconciliationItem> items;
        if (status != null && reason != null) {
            items = reconciliationRepository.findByStatusAndReason(status, reason, pageable);
        } else if (status != null) {
            items = reconciliationRepository.findByStatus(status, pageable);
        } else if (reason != null) {
            items = reconciliationRepository.findByReason(reason, pageable);
        } else {
            items = reconciliationRepository.findAllWithDetails(pageable);
        }

        return PageResponse.from(items, ReconciliationReviewService::toResponse);
    }

    @Transactional(readOnly = true)
    public ReconciliationItemResponse getItem(UUID id) {
        return reconciliationRepository.findWithDetailsById(id)
                .map(ReconciliationReviewService::toResponse)
                .orElseThrow(() -> new ReconciliationItemNotFoundException(id));
    }

    /**
     * Applies a reviewer decision. Moving to RESOLVED stamps {@code resolvedAt};
     * reopening clears it so the item reads as outstanding again.
     */
    @Transactional
    public ReconciliationItemResponse updateItem(UUID id, UpdateReconciliationItemRequest request) {
        ReconciliationItem item = reconciliationRepository.findWithDetailsById(id)
                .orElseThrow(() -> new ReconciliationItemNotFoundException(id));

        if (request.notes() != null) {
            item.setNotes(request.notes().isBlank() ? null : request.notes().trim());
        }

        if (request.status() != null && !request.status().isBlank()) {
            ReconciliationStatus target = parseStatus(request.status());
            if (target == null) {
                throw new InvalidReconciliationRequestException(
                        "Unknown status: " + request.status() + ". Expected one of "
                                + List.of(ReconciliationStatus.values()));
            }
            applyStatus(item, target);
        }

        ReconciliationItem saved = reconciliationRepository.save(item);
        log.info("Reconciliation item {} is now {} ({})", saved.getId(), saved.getStatus(), saved.getReason());
        return toResponse(saved);
    }

    private void applyStatus(ReconciliationItem item, ReconciliationStatus target) {
        ReconciliationStatus current = item.getStatus();
        if (current == target) {
            return; // idempotent: re-asserting the current status is not an error
        }
        if (!ALLOWED_TRANSITIONS.getOrDefault(current, Set.of()).contains(target)) {
            throw new InvalidReconciliationRequestException(
                    "Cannot move a reconciliation item from " + current + " to " + target);
        }

        item.setStatus(target);
        item.setResolvedAt(target == ReconciliationStatus.RESOLVED ? Instant.now() : null);
    }

    @Cacheable("reconciliationSummary")
    @Transactional(readOnly = true)
    public ReconciliationSummaryResponse summary() {
        Map<String, Long> byStatus = countsFor(reconciliationRepository.groupedStatusCounts());
        Map<String, Long> byReason = countsFor(reconciliationRepository.groupedReasonCounts());

        // list every enum value so the dashboard renders a stable set of tiles
        Map<String, Long> statusCounts = new LinkedHashMap<>();
        for (ReconciliationStatus s : ReconciliationStatus.values()) {
            statusCounts.put(s.name(), byStatus.getOrDefault(s.name(), 0L));
        }
        Map<String, Long> reasonCounts = new LinkedHashMap<>();
        for (ReconciliationReason r : ReconciliationReason.values()) {
            reasonCounts.put(r.name(), byReason.getOrDefault(r.name(), 0L));
        }

        Map<String, Long> txByStatus = new LinkedHashMap<>();
        for (String s : List.of("PENDING", "RECONCILED", "MISMATCHED", "ORPHAN")) {
            txByStatus.put(s, transactionRepository.countByReconciliationStatus(s));
        }

        BigDecimal exposure = reconciliationRepository.sumUnresolvedAmountDelta(
                ReconciliationStatus.RESOLVED, ReconciliationReason.AMOUNT_MISMATCH);

        long total = statusCounts.values().stream().mapToLong(Long::longValue).sum();

        return new ReconciliationSummaryResponse(
                total,
                statusCounts.getOrDefault(ReconciliationStatus.OPEN.name(), 0L),
                statusCounts.getOrDefault(ReconciliationStatus.INVESTIGATING.name(), 0L),
                statusCounts.getOrDefault(ReconciliationStatus.RESOLVED.name(), 0L),
                statusCounts,
                reasonCounts,
                txByStatus,
                exposure == null ? BigDecimal.ZERO : exposure);
    }

    private static Map<String, Long> countsFor(List<Object[]> rows) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Object[] row : rows) {
            counts.put(String.valueOf(row[0]), ((Number) row[1]).longValue());
        }
        return counts;
    }

    private static ReconciliationStatus parseStatus(String value) {
        if (value == null || value.isBlank() || "ALL".equalsIgnoreCase(value)) {
            return null;
        }
        try {
            return ReconciliationStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static ReconciliationReason parseReason(String value) {
        if (value == null || value.isBlank() || "ALL".equalsIgnoreCase(value)) {
            return null;
        }
        try {
            return ReconciliationReason.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static ReconciliationItemResponse toResponse(ReconciliationItem item) {
        Transaction tx = item.getTransaction();
        SettlementRecord st = item.getSettlementRecord();

        BigDecimal txAmount = tx != null ? tx.getAmount() : null;
        BigDecimal stAmount = st != null ? st.getAmount() : null;
        BigDecimal delta = (txAmount != null && stAmount != null) ? txAmount.subtract(stAmount) : null;

        // whichever side exists carries the identifying reference
        String provider = tx != null ? tx.getProvider() : (st != null ? st.getProvider() : null);
        String reference = tx != null ? tx.getProviderReference()
                : (st != null ? st.getProviderReference() : null);
        String currency = tx != null ? tx.getCurrency() : (st != null ? st.getCurrency() : null);

        return new ReconciliationItemResponse(
                item.getId(),
                item.getReason() != null ? item.getReason().name() : null,
                item.getStatus() != null ? item.getStatus().name() : null,
                item.getNotes(),
                item.getCreatedAt(),
                item.getResolvedAt(),
                provider,
                reference,
                currency,
                txAmount,
                tx != null ? tx.getStatus() : null,
                tx != null ? tx.getCustomerReference() : null,
                tx != null ? tx.getReceivedAt() : null,
                stAmount,
                st != null ? st.getFee() : null,
                st != null ? st.getStatus() : null,
                st != null ? st.getSettledAt() : null,
                delta);
    }

    /** Thrown when a reviewer references an item id that does not exist. */
    public static class ReconciliationItemNotFoundException extends RuntimeException {
        public ReconciliationItemNotFoundException(UUID id) {
            super("No reconciliation item with id " + id);
        }
    }

    /** Thrown for a malformed status or a disallowed transition. */
    public static class InvalidReconciliationRequestException extends RuntimeException {
        public InvalidReconciliationRequestException(String message) {
            super(message);
        }
    }
}
