package com.basilcode.payment_reconciliation_engine.scheduler;


import com.basilcode.payment_reconciliation_engine.components.Normalizer;
import com.basilcode.payment_reconciliation_engine.records.TransactionRecords;
import com.basilcode.payment_reconciliation_engine.entity.SettlementRecord;
import com.basilcode.payment_reconciliation_engine.repositories.SettlementRepository;
import com.basilcode.payment_reconciliation_engine.service.PaystackClient;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@AllArgsConstructor
public class GetProviderTransactions {

    private final PaystackClient paystackClient;
    private final Normalizer normalizer;
    private final SettlementRepository settlementRepository;

    @Transactional
    public void fetchAndSavePaystackTransactions() {
        try {
            // Get yesterday's date
            LocalDate yesterday = LocalDate.now().minusDays(1);
            String date = yesterday.format(DateTimeFormatter.ISO_LOCAL_DATE);

            log.info("Fetching Paystack transactions for date: {}", date);
            List<JsonNode> rawTransactions = paystackClient.getAllTransactionsForDate(date);

            if (rawTransactions == null || rawTransactions.isEmpty()) {
                log.warn("No transactions found for date: {}", date);
                return;
            }

            // Apply normalizer to all transactions before saving
            List<TransactionRecords> normalizedTransactions = rawTransactions.stream()
                    .map(normalizer::transactionNormalizer)  // <-- Applying normalizer here
                    .collect(Collectors.toList());

            // Map the canonical records onto settlement entities before persisting
            List<SettlementRecord> settlements = normalizedTransactions.stream()
                    .map(GetProviderTransactions::toSettlementRecord)
                    .collect(Collectors.toList());

            // Save all normalized transactions
            List<SettlementRecord> savedTransactions = settlementRepository.saveAll(settlements);

            log.info("Successfully saved {} transactions for date: {}", savedTransactions.size(), date);

        } catch (Exception e) {
            log.error("Error fetching and saving transactions: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to fetch and save transactions", e);
        }
    }

    private static SettlementRecord toSettlementRecord(TransactionRecords record) {
        SettlementRecord settlement = new SettlementRecord();
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