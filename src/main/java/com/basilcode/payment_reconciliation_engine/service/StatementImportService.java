package com.basilcode.payment_reconciliation_engine.service;

import com.basilcode.payment_reconciliation_engine.dto.ImportResultResponse;
import com.basilcode.payment_reconciliation_engine.entity.SettlementRecord;
import com.basilcode.payment_reconciliation_engine.repositories.SettlementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;



@Service
@Slf4j
@RequiredArgsConstructor
public class StatementImportService {

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy")
    );

    private final SettlementRepository settlementRepository;
    private final ReconciliationEngine reconciliationEngine;

    @CacheEvict(cacheNames = "reconciliationSummary", allEntries = true)
    @Transactional
    public ImportResultResponse importCsv(MultipartFile file, String provider, boolean reconcileAfter) {
        List<String> errors = new ArrayList<>();
        List<SettlementRecord> toSave = new ArrayList<>();
        int created = 0;
        int updated = 0;
        int rowIndex = 0;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {

            String headerLine = reader.readLine();
            if (headerLine == null) {
                return new ImportResultResponse(0, 0, 0, List.of("File is empty"), null);
            }

            Map<String, Integer> colIndex = parseHeaders(headerLine);
            validateRequiredHeaders(colIndex);

            List<String> references = new ArrayList<>();
            List<String[]> parsedRows = new ArrayList<>();

            String line;
            while ((line = reader.readLine()) != null) {
                rowIndex++;
                if (line.isBlank()) continue;
                String[] cols = splitCsv(line);
                try {
                    references.add(requireColumn(cols, colIndex, "reference", rowIndex));
                    parsedRows.add(cols);
                } catch (ImportParseException e) {
                    errors.add(e.getMessage());
                }
            }

            Map<String, SettlementRecord> existing = settlementRepository
                    .findByProviderAndProviderReferenceIn(provider, references)
                    .stream()
                    .collect(Collectors.toMap(SettlementRecord::getProviderReference, s -> s));

            for (int i = 0; i < parsedRows.size(); i++) {
                String[] cols = parsedRows.get(i);
                int row = i + 1;
                try {
                    String ref        = requireColumn(cols, colIndex, "reference", row);
                    BigDecimal amount = parseMoney(requireColumn(cols, colIndex, "amount", row), row);
                    String currency   = requireColumn(cols, colIndex, "currency", row).toUpperCase();
                    String status     = requireColumn(cols, colIndex, "status", row).toLowerCase();
                    Instant settledAt = parseDate(requireColumn(cols, colIndex, "settled_at", row), row);
                    BigDecimal fee    = optionalMoney(cols, colIndex, "fee");

                    SettlementRecord settlement = existing.getOrDefault(ref, new SettlementRecord());
                    boolean isNew = settlement.getId() == null;

                    settlement.setProvider(provider);
                    settlement.setProviderReference(ref);
                    settlement.setAmount(amount);
                    settlement.setCurrency(currency);
                    settlement.setStatus(status);
                    settlement.setSettledAt(settledAt);
                    settlement.setFee(fee);
                    settlement.setImportedAt(Instant.now());

                    toSave.add(settlement);
                    if (isNew) created++; else updated++;

                } catch (ImportParseException e) {
                    errors.add(e.getMessage());
                }
            }

            settlementRepository.saveAll(toSave);
            log.info("Statement import provider={}: {} created, {} updated, {} errors",
                    provider, created, updated, errors.size());

        } catch (ImportParseException e) {
            return new ImportResultResponse(0, 0, 0, List.of(e.getMessage()), null);
        } catch (Exception e) {
            log.error("Statement import failed: {}", e.getMessage(), e);
            return new ImportResultResponse(0, 0, rowIndex, List.of("Parse failed: " + e.getMessage()), null);
        }

        ReconciliationEngine.PassResult passResult = null;
        if (reconcileAfter) {
            try {
                passResult = reconciliationEngine.reconciliation();
            } catch (Exception e) {
                log.error("Post-import reconciliation failed: {}", e.getMessage(), e);
                errors.add("Reconciliation pass failed: " + e.getMessage());
            }
        }

        return new ImportResultResponse(created, updated, errors.size(), errors, passResult);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private static Map<String, Integer> parseHeaders(String headerLine) {
        String[] headers = splitCsv(headerLine);
        Map<String, Integer> index = new LinkedHashMap<>();
        for (int i = 0; i < headers.length; i++) {
            String key = headers[i].trim().toLowerCase()
                    .replace(' ', '_').replace('-', '_');
            if ("date".equals(key)) key = "settled_at"; // alias
            index.put(key, i);
        }
        return index;
    }

    private static void validateRequiredHeaders(Map<String, Integer> colIndex) {
        List<String> required = List.of("reference", "amount", "currency", "status", "settled_at");
        List<String> missing  = required.stream().filter(h -> !colIndex.containsKey(h)).toList();
        if (!missing.isEmpty()) {
            throw new ImportParseException(
                    "Missing required columns: " + missing + ". Got: " + colIndex.keySet());
        }
    }

    private static String requireColumn(String[] cols, Map<String, Integer> colIndex,
                                        String name, int row) {
        Integer idx = colIndex.get(name);
        if (idx == null || idx >= cols.length) {
            throw new ImportParseException("Row " + row + ": missing column '" + name + "'");
        }
        String val = cols[idx].trim();
        if (val.isEmpty()) {
            throw new ImportParseException("Row " + row + ": column '" + name + "' is blank");
        }
        return val;
    }

    private static BigDecimal parseMoney(String raw, int row) {
        try {
            // Strip currency symbols and thousands separators, e.g. "NGN 1,200.00" -> "1200.00"
            return new BigDecimal(raw.replaceAll("[^\\d.]", ""));
        } catch (NumberFormatException e) {
            throw new ImportParseException("Row " + row + ": cannot parse amount '" + raw + "'");
        }
    }

    private static BigDecimal optionalMoney(String[] cols, Map<String, Integer> colIndex, String name) {
        Integer idx = colIndex.get(name);
        if (idx == null || idx >= cols.length) return null;
        String val = cols[idx].trim();
        if (val.isEmpty()) return null;
        try {
            return new BigDecimal(val.replaceAll("[^\\d.]", ""));
        } catch (NumberFormatException e) {
            return null; // fee is optional — silently skip bad values
        }
    }

    private static Instant parseDate(String raw, int row) {
        String trimmed = raw.trim();
        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try {
                return Instant.from(fmt.parse(trimmed));
            } catch (Exception ignored) {
                try {
                    return LocalDate.parse(trimmed, fmt).atStartOfDay().toInstant(ZoneOffset.UTC);
                } catch (Exception ignored2) { /* try next format */ }
            }
        }
        throw new ImportParseException("Row " + row + ": cannot parse date '" + raw + "'");
    }

    /** Minimal CSV splitter that handles quoted fields containing commas. */
    private static String[] splitCsv(String line) {
        List<String> tokens = new ArrayList<>();
        boolean inQuote = false;
        StringBuilder sb = new StringBuilder();
        for (char c : line.toCharArray()) {
            if (c == '"')              { inQuote = !inQuote; }
            else if (c == ',' && !inQuote) { tokens.add(sb.toString()); sb.setLength(0); }
            else                       { sb.append(c); }
        }
        tokens.add(sb.toString());
        return tokens.toArray(new String[0]);
    }

    /** Thrown for a single-row parse error; allows the import to continue with remaining rows. */
    static class ImportParseException extends RuntimeException {
        ImportParseException(String msg) { super(msg); }
    }
}
