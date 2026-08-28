package com.basilcode.payment_reconciliation_engine.service;

import com.basilcode.payment_reconciliation_engine.entity.Transaction;
import com.basilcode.payment_reconciliation_engine.repositories.TransactionRepository;
import lombok.AllArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

@Service
@AllArgsConstructor
public class TransactionService {

    private KafkaMessagePublisher kafkaMessagePublisher;

    public void saveFromPaystackWebhook(JsonNode data) {
        kafkaMessagePublisher.PaymentFromPaystack(data);
    }
}
