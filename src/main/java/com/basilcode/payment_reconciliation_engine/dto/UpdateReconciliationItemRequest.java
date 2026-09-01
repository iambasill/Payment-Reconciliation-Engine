package com.basilcode.payment_reconciliation_engine.dto;

/**
 * Reviewer action on a reconciliation item. {@code status} must be one of
 * OPEN, INVESTIGATING or RESOLVED; {@code notes} is optional and, when
 * present, replaces the existing note.
 */
public record UpdateReconciliationItemRequest(
        String status,
        String notes
) {}
