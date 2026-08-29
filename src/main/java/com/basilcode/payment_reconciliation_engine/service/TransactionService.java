package com.basilcode.payment_reconciliation_engine.service;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

@Service
@AllArgsConstructor
public class TransactionService {

    private KafkaMessagePublisher kafkaMessagePublisher;

    public void saveFromPaystackWebhook(JsonNode data) {
        kafkaMessagePublisher.PaymentFromPaystack(data);
    }
}
