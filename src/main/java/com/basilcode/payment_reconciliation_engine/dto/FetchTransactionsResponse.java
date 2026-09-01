package com.basilcode.payment_reconciliation_engine.dto;

/**
 * Response returned by the manual fetch-transactions endpoint.
 * {@code date} is the target date string, or "all" when no date filter was applied.
 */
public record FetchTransactionsResponse(
        int fetched,
        int created,
        int updated,
        String date
) {}
