package com.basilcode.payment_reconciliation_engine.repositories;

import com.basilcode.payment_reconciliation_engine.entity.SettlementRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SettlementRepository extends JpaRepository<SettlementRecord, UUID> {
}
