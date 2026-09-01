package com.basilcode.payment_reconciliation_engine.controller;

import com.basilcode.payment_reconciliation_engine.dto.ImportResultResponse;
import com.basilcode.payment_reconciliation_engine.dto.PageResponse;
import com.basilcode.payment_reconciliation_engine.dto.ReconciliationItemResponse;
import com.basilcode.payment_reconciliation_engine.dto.ReconciliationSummaryResponse;
import com.basilcode.payment_reconciliation_engine.dto.UpdateReconciliationItemRequest;
import com.basilcode.payment_reconciliation_engine.service.ReconciliationEngine;
import com.basilcode.payment_reconciliation_engine.service.ReconciliationReviewService;
import com.basilcode.payment_reconciliation_engine.service.ReconciliationReviewService.InvalidReconciliationRequestException;
import com.basilcode.payment_reconciliation_engine.service.ReconciliationReviewService.ReconciliationItemNotFoundException;
import com.basilcode.payment_reconciliation_engine.service.StatementImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

/**
 * Review queue for reconciliation discrepancies.
 *
 * <pre>
 * GET   /reconciliation/summary                    headline counts
 * GET   /reconciliation/items?status=&amp;reason=      paged queue
 * GET   /reconciliation/items/{id}                 one item
 * PATCH /reconciliation/items/{id}                 set status and/or notes
 * POST  /reconciliation/run                        run a matching pass now
 * </pre>
 */
@RestController
@RequestMapping("/reconciliation")
@RequiredArgsConstructor
public class ReconciliationController {

    private final ReconciliationReviewService reviewService;
    private final ReconciliationEngine reconciliationEngine;
    private final StatementImportService statementImportService;

    @GetMapping("/summary")
    public ReconciliationSummaryResponse summary() {
        return reviewService.summary();
    }

    @GetMapping("/items")
    public PageResponse<ReconciliationItemResponse> listItems(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String reason,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return reviewService.listItems(status, reason, page, size);
    }

    @GetMapping("/items/{id}")
    public ReconciliationItemResponse getItem(@PathVariable UUID id) {
        return reviewService.getItem(id);
    }

    @PatchMapping("/items/{id}")
    public ReconciliationItemResponse updateItem(@PathVariable UUID id,
                                                 @RequestBody UpdateReconciliationItemRequest request) {
        return reviewService.updateItem(id, request);
    }


    @PostMapping("/run")
    public ResponseEntity<ReconciliationSummaryResponse> run() {
        reconciliationEngine.reconciliation();
        return ResponseEntity.ok(reviewService.summary());
    }


    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportResultResponse> importStatement(
            @RequestParam("file") MultipartFile file,
            @RequestParam("provider") String provider,
            @RequestParam(defaultValue = "true") boolean reconcileAfter) {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(new ImportResultResponse(0, 0, 0,
                            java.util.List.of("Uploaded file is empty"), null));
        }
        if (provider == null || provider.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(new ImportResultResponse(0, 0, 0,
                            java.util.List.of("'provider' param is required"), null));
        }

        ImportResultResponse result = statementImportService.importCsv(file, provider.trim().toLowerCase(), reconcileAfter);
        HttpStatus status = result.skipped() > 0 && result.created() == 0 && result.updated() == 0
                ? HttpStatus.UNPROCESSABLE_ENTITY
                : HttpStatus.OK;
        return ResponseEntity.status(status).body(result);
    }

    @ExceptionHandler(ReconciliationItemNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(ReconciliationItemNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(InvalidReconciliationRequestException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(InvalidReconciliationRequestException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
}
