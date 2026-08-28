package com.basilcode.payment_reconciliation_engine.controller;

import com.basilcode.payment_reconciliation_engine.dto.InitializeTransactionRequest;
import com.basilcode.payment_reconciliation_engine.dto.PaystackInitializeResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final RestClient paystackRestClient;

    public PaymentController(RestClient paystackRestClient) {
        this.paystackRestClient = paystackRestClient;
    }

    @PostMapping("/initialize")
    public ResponseEntity<PaystackInitializeResponse> initialize(
            @RequestBody InitializeTransactionRequest request) {

        PaystackInitializeResponse response = paystackRestClient.post()
                .uri("/transaction/initialize")
                .body(request)
                .retrieve()
                .body(PaystackInitializeResponse.class);

        return ResponseEntity.ok(response);
    }
}