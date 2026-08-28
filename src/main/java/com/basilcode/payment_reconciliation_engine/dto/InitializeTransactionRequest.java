package com.basilcode.payment_reconciliation_engine.dto;

public record InitializeTransactionRequest(
        String email,
        long amount)
{}