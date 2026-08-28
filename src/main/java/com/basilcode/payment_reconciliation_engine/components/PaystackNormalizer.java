package com.basilcode.payment_reconciliation_engine.components;


import com.basilcode.payment_reconciliation_engine.records.TransactionEvent;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;

@Component
public class PaystackNormalizer {
    public TransactionEvent normalize(JsonNode data) {
        return new TransactionEvent(
                "paystack",
                data.get("reference").asString(),
                BigDecimal.valueOf(data.get("amount").asLong()).divide(BigDecimal.valueOf(100)),
                data.get("currency").asString(),
                data.get("status").asString(),
                data.get("customer").get("email").asString(),
                Instant.parse(data.get("paid_at").asString())
        );
    }
}