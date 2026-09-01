package com.basilcode.payment_reconciliation_engine.repositories;


import com.basilcode.payment_reconciliation_engine.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    List<Transaction> findByReconciliationStatus(String reconciliationStatus);

    List<Transaction> findByReconciliationStatusIn(Collection<String> reconciliationStatuses);

    Optional<Transaction> findByProviderAndProviderReference(String provider, String providerReference);

    long countByReconciliationStatus(String reconciliationStatus);
}
