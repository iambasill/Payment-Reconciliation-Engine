package com.basilcode.payment_reconciliation_engine.service;

import com.basilcode.payment_reconciliation_engine.components.PaystackNormalizer;
import com.basilcode.payment_reconciliation_engine.entity.Transaction;
import com.basilcode.payment_reconciliation_engine.records.TransactionEvent;
import com.basilcode.payment_reconciliation_engine.repositories.TransactionRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

@Service
@Slf4j
@AllArgsConstructor
public class KafkaMessageListener {
    private final PaystackNormalizer paystackNormalizer;
    private final TransactionRepository transactionRepository;

    @KafkaListener(topics = "payments.paystack.raw")
    public void processPaymentListener(JsonNode data) {
        TransactionEvent event = paystackNormalizer.normalize(data);

        Transaction transaction = new Transaction();
        transaction.setProvider(event.provider());
        transaction.setProviderReference(event.providerReference());
        transaction.setAmount(event.amount());
        transaction.setCurrency(event.currency());
        transaction.setStatus(event.status());
        transaction.setCustomerReference(event.customerReference());
        transaction.setReceivedAt(event.receivedAt());

        try {
            transactionRepository.save(transaction);
            log.info("Saved new transaction: {}", event.providerReference());
        } catch (DataIntegrityViolationException e) {
            log.warn("Duplicate webhook ignored for reference: {}", event.providerReference());
        }
    }
}
