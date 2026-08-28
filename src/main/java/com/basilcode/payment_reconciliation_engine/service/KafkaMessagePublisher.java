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
    private KafkaTemplate<String, Object> kafkaTemplate;

    public void PaymentFromPaystack(JsonNode data) {
        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send("demo", data);
        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("Sent successfully: " + result.getRecordMetadata());
            } else {
                log.error("Send failed: " + ex.getMessage());
            }
        });
    }


}
