package com.basilcode.payment_reconciliation_engine.records;

import java.math.BigDecimal;
import java.time.Instant;

public record WebhookRecords(
        String provider, String providerReference, BigDecimal amount,
        String currency, String status, String customerReference, Instant receivedAt
) {}

