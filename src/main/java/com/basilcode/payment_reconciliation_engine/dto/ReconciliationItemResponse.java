package com.basilcode.payment_reconciliation_engine.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Flattened view of a reconciliation item for the review queue. Both sides are
 * nullable: an ORPHAN_TRANSACTION has no settlement, a MISSING_WEBHOOK has no
 * transaction.
 */
public record ReconciliationItemResponse(
        UUID id,
        String reason,
        String status,
        String notes,
        Instant createdAt,
        Instant resolvedAt,

        String provider,
        String providerReference,
        String currency,

        BigDecimal transactionAmount,
        String transactionStatus,
        String customerReference,
        Instant receivedAt,

        BigDecimal settlementAmount,
        BigDecimal settlementFee,
        String settlementStatus,
        Instant settledAt,

        /** transactionAmount - settlementAmount; null when either side is absent */
        BigDecimal amountDelta
) {}
