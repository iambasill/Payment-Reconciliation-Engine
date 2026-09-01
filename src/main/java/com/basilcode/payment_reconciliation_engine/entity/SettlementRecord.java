package com.basilcode.payment_reconciliation_engine.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "settlement_records",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_settlement_provider_reference",
                columnNames = {"provider", "provider_reference"})
)

@Data
public class SettlementRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String provider; // "paystack", "bank_transfer"

    @Column(name = "provider_reference", nullable = false)
    private String providerReference; // must match Transaction.providerReference to be comparable

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount; // the amount the provider says actually settled (after fees)

    @Column(precision = 19, scale = 4)
    private BigDecimal fee; // fee deducted, if provided (nullable — bank statements won't have this)

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false)
    private String status; // "settled", "reversed", "failed"

    @Column(name = "settled_at", nullable = false)
    private Instant settledAt;

    @Column(name = "imported_at", nullable = false)
    private Instant importedAt; // when YOU pulled/uploaded this record — useful for audit

    @Version
    private Long version;

}