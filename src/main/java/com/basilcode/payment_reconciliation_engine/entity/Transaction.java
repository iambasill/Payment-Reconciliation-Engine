package com.basilcode.payment_reconciliation_engine.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "transactions"
)
@Data
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String provider; // e.g. "paystack", "bank_transfer"

    @Column(name = "provider_reference", nullable = false)
    private String providerReference; // Paystack's "reference" field — this IS your idempotency key

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount; // ALWAYS BigDecimal for money, never double/float

    @Column(nullable = false, length = 3)
    private String currency; // "NGN"

    @Column(nullable = false)
    private String status; // "success", "failed", "pending"

    @Column(name = "customer_reference")
    private String customerReference; // email or customer id

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt; // when YOUR system got the webhook

    @Version
    private Long version; // optimistic locking — needed for the concurrency test later
}