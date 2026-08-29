package com.basilcode.payment_reconciliation_engine.components;

import com.basilcode.payment_reconciliation_engine.records.TransactionRecords;
import com.basilcode.payment_reconciliation_engine.records.WebhookRecords;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;

@Component
public class Normalizer {

    private static final Logger log = LoggerFactory.getLogger(Normalizer.class);
    private static final String PROVIDER = "paystack";
    private static final String DEFAULT_CURRENCY = "NGN";
    private static final BigDecimal MINOR_UNIT_DIVISOR = BigDecimal.valueOf(100);

    public WebhookRecords webhookNormalizer(JsonNode data) {
        requireNonNull(data, "Webhook data cannot be null");

        String reference = textOrNull(data, "reference");
        BigDecimal amount = toMainUnit(longOrDefault(data, "amount", 0L));
        String currency = textOrDefault(data, "currency", DEFAULT_CURRENCY);
        String status = textOrDefault(data, "status", "unknown");
        String customerEmail = data.path("customer").has("email")
                ? data.path("customer").get("email").asText()
                : null;
        Instant paidAt = instantOrNow(data, "paid_at");

        return new WebhookRecords(PROVIDER, reference, amount, currency, status, customerEmail, paidAt);
    }

    public TransactionRecords transactionNormalizer(JsonNode data) {
        requireNonNull(data, "Transaction data cannot be null");

        String reference = textOrNull(data, "reference");
        if (reference == null) {
            throw new IllegalArgumentException("Missing required field: reference");
        }

        if (!data.has("amount") || data.get("amount").isNull()) {
            throw new IllegalArgumentException("Missing required field: amount");
        }
        BigDecimal amount = toMainUnit(data.get("amount").asLong());

        String currency = textOrDefault(data, "currency", DEFAULT_CURRENCY);
        String status = normalizeStatus(textOrDefault(data, "status", "unknown"));
        String customerEmail = data.path("customer").has("email")
                ? data.path("customer").get("email").asText()
                : null;
        Instant paidAt = instantOrNow(data, "paid_at");

        BigDecimal fee = data.has("fees") && !data.get("fees").isNull()
                ? toMainUnit(data.get("fees").asLong())
                : null;

        String transactionType = textOrDefault(data, "transaction_type", "charge");
        String channel = textOrNull(data, "channel");
        String gatewayResponse = textOrNull(data, "gateway_response");
        String ipAddress = textOrNull(data, "ip_address");
        Instant createdAt = instantOrNow(data, "created_at");

        return new TransactionRecords(
                PROVIDER,
                reference,
                amount,
                currency,
                status,
                customerEmail,
                paidAt,
                fee,
                transactionType,
                channel,
                gatewayResponse,
                ipAddress,
                createdAt
        );
    }

    private static void requireNonNull(JsonNode data, String message) {
        if (data == null || data.isNull()) {
            throw new IllegalArgumentException(message);
        }
    }

    private static String textOrNull(JsonNode data, String field) {
        return data.has(field) && !data.get(field).isNull() ? data.get(field).asText() : null;
    }

    private static String textOrDefault(JsonNode data, String field, String defaultValue) {
        String value = textOrNull(data, field);
        return value != null ? value : defaultValue;
    }

    private static long longOrDefault(JsonNode data, String field, long defaultValue) {
        return data.has(field) && !data.get(field).isNull() ? data.get(field).asLong() : defaultValue;
    }

    private static Instant instantOrNow(JsonNode data, String field) {
        return data.has(field) && !data.get(field).isNull()
                ? Instant.parse(data.get(field).asText())
                : Instant.now();
    }

    private static BigDecimal toMainUnit(long minorUnits) {
        return BigDecimal.valueOf(minorUnits).divide(MINOR_UNIT_DIVISOR);
    }

    private static String normalizeStatus(String status) {
        if (status == null) {
            return "failed";
        }
        return switch (status.toLowerCase().trim()) {
            case "settled", "success", "completed" -> "settled";
            case "reversed", "refunded" -> "reversed";
            default -> "failed";
        };
    }
}