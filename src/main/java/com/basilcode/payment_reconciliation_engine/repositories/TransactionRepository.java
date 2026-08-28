package com.basilcode.payment_reconciliation_engine.repositories;


import com.basilcode.payment_reconciliation_engine.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
}
