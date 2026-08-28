package com.basilcode.payment_reconciliation_engine.repositories;

import com.basilcode.payment_reconciliation_engine.entity.ReconciliationItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ReconciliationRepository extends JpaRepository <ReconciliationItem, UUID> {

}
