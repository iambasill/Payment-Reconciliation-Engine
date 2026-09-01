package com.basilcode.payment_reconciliation_engine.repositories;

import com.basilcode.payment_reconciliation_engine.entity.SettlementRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SettlementRepository extends JpaRepository<SettlementRecord, UUID> {

    Optional<SettlementRecord> findByProviderAndProviderReference(String provider, String providerReference);

    List<SettlementRecord> findByProviderAndProviderReferenceIn(String provider, List<String> providerReferences);

    @Query("""
            select s from SettlementRecord s
            where not exists (
                select 1 from Transaction t
                where t.provider = s.provider
                  and t.providerReference = s.providerReference
            )
            """)
    List<SettlementRecord> findWithoutMatchingTransaction();
}
