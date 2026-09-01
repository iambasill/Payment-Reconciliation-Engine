package com.basilcode.payment_reconciliation_engine.dto;

import java.math.BigDecimal;
import java.util.Map;

/** Headline figures for the reconciliation dashboard. */
public record ReconciliationSummaryResponse(
        long totalItems,
        long openItems,
        long investigatingItems,
        long resolvedItems,
        Map<String, Long> byStatus,
        Map<String, Long> byReason,
        Map<String, Long> transactionsByReconciliationStatus,
        /** Sum of |transaction - settlement| across unresolved amount mismatches. */
        BigDecimal unresolvedExposure
) {}
