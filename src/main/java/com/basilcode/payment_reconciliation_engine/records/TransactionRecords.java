package com.basilcode.payment_reconciliation_engine.records;

import java.math.BigDecimal;
import java.time.Instant;


import java.math.BigDecimal;
import java.time.Instant;

public record TransactionRecords(
        String provider,
        String providerReference,
        BigDecimal amount,
        String currency,
        String status,
        String customerEmail,
        Instant paidAt,
        BigDecimal fee,           // Fee deducted by Paystack
        String transactionType,   // "charge", "refund", "settlement"
        String channel,           // "card", "bank", "ussd", etc.
        String gatewayResponse,   // "Successful", "Declined", etc.
        String ipAddress,
        Instant createdAt
) {}
