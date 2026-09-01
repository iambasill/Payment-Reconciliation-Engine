package com.basilcode.payment_reconciliation_engine.dto;

import com.basilcode.payment_reconciliation_engine.service.ReconciliationEngine;

import java.util.List;

public record ImportResultResponse(
        int created,
        int updated,
        int skipped,
        List<String> errors,
        ReconciliationEngine.PassResult passResult
) {}
