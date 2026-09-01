package com.basilcode.payment_reconciliation_engine.scheduler;

import com.basilcode.payment_reconciliation_engine.components.Normalizer;
import com.basilcode.payment_reconciliation_engine.dto.FetchTransactionsResponse;
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
            int[] counts = upsertSettlements(rawTransactions);
            log.info("Imported settlements for {}: {} new, {} refreshed", date, counts[0], counts[1]);
        } catch (Exception e) {
            log.error("Error fetching and saving transactions: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to fetch and save transactions", e);
        }
    }

    /**
     * Manual trigger — fetch transactions for a specific date from Paystack and upsert.
     */
    @Transactional
    public FetchTransactionsResponse manualFetchByDate(String date) {
        log.info("Manual fetch Paystack transactions for date: {}", date);
        List<JsonNode> raw = paystackClient.getAllTransactionsForDate(date);
        if (raw == null || raw.isEmpty()) {
            return new FetchTransactionsResponse(0, 0, 0, date);
        }
        int[] counts = upsertSettlements(raw);
        return new FetchTransactionsResponse(raw.size(), counts[0], counts[1], date);
    }

    /**
     * Manual trigger — fetch ALL transactions from Paystack (no date filter) and upsert.
     * Paginates through the full list; may be slow for large accounts.
     */
    @Transactional
    public FetchTransactionsResponse manualFetchAll() {
        log.info("Manual fetch ALL Paystack transactions");
        List<JsonNode> allRaw = new ArrayList<>();
        int page = 1;
        int perPage = 100;
        while (true) {
            JsonNode response = paystackClient.listTransactions(perPage, page);
            JsonNode data = response.get("data");
            if (data == null || !data.isArray() || !data.iterator().hasNext()) break;
            for (JsonNode tx : data) allRaw.add(tx);
            JsonNode meta = response.get("meta");
            if (meta == null || page >= resolvePageCount(meta)) break;
            page++;
        }
        if (allRaw.isEmpty()) {
            return new FetchTransactionsResponse(0, 0, 0, "all");
        }
        int[] counts = upsertSettlements(allRaw);
        return new FetchTransactionsResponse(allRaw.size(), counts[0], counts[1], "all");
    }

    // ---------------------------------------------------------------------------
    // Private helpers
    // ---------------------------------------------------------------------------

    /**
     * Normalizes raw Paystack JsonNodes and upserts them as SettlementRecords.
     *
     * @return int[]{created, updated}
     */
    private int[] upsertSettlements(List<JsonNode> rawTransactions) {
        List<TransactionRecords> normalized = rawTransactions.stream()
                .map(normalizer::transactionNormalizer)
                .collect(Collectors.toList());

        List<String> references = normalized.stream()
                .map(TransactionRecords::providerReference)
                .toList();

        Map<String, SettlementRecord> existingByReference = settlementRepository
                .findByProviderAndProviderReferenceIn("paystack", references)
                .stream()
                .collect(Collectors.toMap(SettlementRecord::getProviderReference, r -> r));

        int created = 0, updated = 0;
        List<SettlementRecord> settlements = new ArrayList<>(normalized.size());

        for (TransactionRecords record : normalized) {
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
        return new int[]{created, updated};
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

    /**
     * Paystack returns page count under different field names depending on the endpoint:
     *  - List endpoint (/transaction)          → "pageCount"
     *  - Date-filtered endpoint (from/to)      → "total_pages"
     * Falls back to 1 (single-page) if neither field is present.
     */
    private static int resolvePageCount(JsonNode meta) {
        for (String field : new String[]{"pageCount", "total_pages"}) {
            JsonNode node = meta.get(field);
            if (node != null && !node.isNull()) {
                return node.asInt();
            }
        }
        return 1;
    }
}