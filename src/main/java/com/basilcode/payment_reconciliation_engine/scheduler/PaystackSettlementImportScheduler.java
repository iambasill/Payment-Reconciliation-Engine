package com.basilcode.payment_reconciliation_engine.scheduler;

import com.basilcode.payment_reconciliation_engine.components.Normalizer;
import com.basilcode.payment_reconciliation_engine.entity.SettlementRecord;
import com.basilcode.payment_reconciliation_engine.records.TransactionRecords;
import com.basilcode.payment_reconciliation_engine.repositories.SettlementRepository;
import com.basilcode.payment_reconciliation_engine.service.PaystackClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PaystackSettlementImportScheduler {

    private final PaystackClient paystackClient;
    private final Normalizer normalizer;
    private final SettlementRepository settlementRepository;
    private final ZoneId zone;

    public PaystackSettlementImportScheduler(PaystackClient paystackClient,
                                              Normalizer normalizer,
                                              SettlementRepository settlementRepository,
                                              @Value("${reconciliation.zone}") String zone) {
        this.paystackClient = paystackClient;
        this.normalizer = normalizer;
        this.settlementRepository = settlementRepository;
        this.zone = ZoneId.of(zone);
    }

    @Scheduled(cron = "${reconciliation.settlement-import-cron}",
               zone = "${reconciliation.zone}")
    public void importYesterdaysSettlements() {
        LocalDate yesterday = LocalDate.now(zone).minusDays(1);
        fetchAndSavePaystackTransactions(yesterday.format(DateTimeFormatter.ISO_LOCAL_DATE));
    }

    public void fetchAndSavePaystackTransactions() {
        importYesterdaysSettlements();
    }

    @Transactional
    public void fetchAndSavePaystackTransactions(String date) {
        try {
            log.info("Fetching Paystack transactions for date: {}", date);
            List<JsonNode> rawTransactions = paystackClient.getAllTransactionsForDate(date);

            if (rawTransactions == null || rawTransactions.isEmpty()) {
                log.warn("No transactions found for date: {}", date);
                return;
            }

            List<TransactionRecords> normalizedTransactions = rawTransactions.stream()
                    .map(normalizer::transactionNormalizer)
                    .collect(Collectors.toList());

            List<String> references = normalizedTransactions.stream()
                    .map(TransactionRecords::providerReference)
                    .toList();

            Map<String, SettlementRecord> existingByReference = settlementRepository
                    .findByProviderAndProviderReferenceIn("paystack", references)
                    .stream()
                    .collect(Collectors.toMap(SettlementRecord::getProviderReference, r -> r));

            int created = 0;
            int updated = 0;
            List<SettlementRecord> settlements = new ArrayList<>(normalizedTransactions.size());

            for (TransactionRecords record : normalizedTransactions) {
                SettlementRecord existing = existingByReference.get(record.providerReference());
                if (existing != null) {
                    updated++;
                    settlements.add(applyTo(existing, record));
                } else {
                    created++;
                    settlements.add(applyTo(new SettlementRecord(), record));
                }
            }

            settlementRepository.saveAll(settlements);

            log.info("Imported settlements for {}: {} new, {} refreshed", date, created, updated);

        } catch (Exception e) {
            log.error("Error fetching and saving transactions: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to fetch and save transactions", e);
        }
    }

    private static SettlementRecord applyTo(SettlementRecord settlement, TransactionRecords record) {
        settlement.setProvider(record.provider());
        settlement.setProviderReference(record.providerReference());
        settlement.setAmount(record.amount());
        settlement.setFee(record.fee());
        settlement.setCurrency(record.currency());
        settlement.setStatus(record.status());
        settlement.setSettledAt(record.paidAt());
        settlement.setImportedAt(Instant.now());
        return settlement;
    }
}