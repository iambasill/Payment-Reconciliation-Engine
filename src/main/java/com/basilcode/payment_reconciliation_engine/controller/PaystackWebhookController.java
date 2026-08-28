package com.basilcode.payment_reconciliation_engine.controller;

import com.basilcode.payment_reconciliation_engine.service.TransactionService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/webhooks")
public class PaystackWebhookController {

    private final ObjectMapper objectMapper;
    private final TransactionService transactionService;

    @Value("${paystack.secret-key}")
    private String paystackSecretKey;

    public PaystackWebhookController(ObjectMapper objectMapper, TransactionService transactionService) {
        this.objectMapper = objectMapper;
        this.transactionService = transactionService;
    }

    @PostMapping("/paystack")
    public ResponseEntity<String> handlePaystackWebhook(
            @RequestBody String rawPayload,
            @RequestHeader("x-paystack-signature") String signature) {

        if (!isValidSignature(rawPayload, signature)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid signature");
        }

        try {
            JsonNode json = objectMapper.readTree(rawPayload);
            String event = json.get("event").asString();
            JsonNode data = json.get("data");

            if ("charge.success".equals(event)) {
                transactionService.saveFromPaystackWebhook(data);
            }

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Malformed payload");
        }

        return ResponseEntity.ok("Webhook received");
    }

    private boolean isValidSignature(String payload, String receivedSignature) {
        try {
            Mac sha512Hmac = Mac.getInstance("HmacSHA512");
            SecretKeySpec keySpec = new SecretKeySpec(
                    paystackSecretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA512");
            sha512Hmac.init(keySpec);

            byte[] hashBytes = sha512Hmac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String computedSignature = bytesToHex(hashBytes);

            return computedSignature.equals(receivedSignature);
        } catch (Exception e) {
            return false;
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}