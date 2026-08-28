package com.basilcode.payment_reconciliation_engine.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reconciliation_items")
@Data
public class ReconciliationItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id")
    private Transaction transaction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "settlement_record_id")
    private SettlementRecord settlementRecord;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReconciliationReason reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReconciliationStatus status;

    @Column(name = "notes")
    private String notes; // reviewer's comment when resolving

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    public enum ReconciliationReason {
        AMOUNT_MISMATCH,   // both exist, amounts differ
        MISSING_WEBHOOK,   // settlement exists, no matching transaction
        ORPHAN_TRANSACTION,// transaction exists, no matching settlement
        STATUS_MISMATCH    // e.g. transaction says success, settlement says reversed
    }

    public enum ReconciliationStatus {
        OPEN,
        INVESTIGATING,
        RESOLVED
    }
}