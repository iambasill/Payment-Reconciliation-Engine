package com.basilcode.payment_reconciliation_engine.service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.util.concurrent.CompletableFuture;

@Service
@AllArgsConstructor
@Slf4j
public class KafkaMessagePublisher {

    static final String PAYSTACK_RAW_TOPIC = "payments.paystack.raw";

    private KafkaTemplate<String, Object> kafkaTemplate;

    public void PaymentFromPaystack(JsonNode data) {
        // keyed by reference so all events for one transaction land on the same
        // partition and stay ordered relative to each other
        String reference = data.get("reference").asString();

        CompletableFuture<SendResult<String, Object>> future =
                kafkaTemplate.send(PAYSTACK_RAW_TOPIC, reference, data);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("Published {} to {}", reference, result.getRecordMetadata());
            } else {
                log.error("Publish failed for {}: {}", reference, ex.getMessage());
            }
        });
    }
}
