package com.basilcode.payment_reconciliation_engine.repositories;

import com.basilcode.payment_reconciliation_engine.entity.ReconciliationItem;
import com.basilcode.payment_reconciliation_engine.entity.ReconciliationItem.ReconciliationReason;
import com.basilcode.payment_reconciliation_engine.entity.ReconciliationItem.ReconciliationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReconciliationRepository extends JpaRepository<ReconciliationItem, UUID> {

    // the LAZY transaction/settlement associations are needed for the response
    // payload, so fetch them up front rather than lazily per row
    @EntityGraph(attributePaths = {"transaction", "settlementRecord"})
    Page<ReconciliationItem> findByStatus(ReconciliationStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"transaction", "settlementRecord"})
    Page<ReconciliationItem> findByReason(ReconciliationReason reason, Pageable pageable);

    @EntityGraph(attributePaths = {"transaction", "settlementRecord"})
    Page<ReconciliationItem> findByStatusAndReason(ReconciliationStatus status,
                                                  ReconciliationReason reason,
                                                  Pageable pageable);

    @EntityGraph(attributePaths = {"transaction", "settlementRecord"})
    @Query("select i from ReconciliationItem i")
    Page<ReconciliationItem> findAllWithDetails(Pageable pageable);

    @EntityGraph(attributePaths = {"transaction", "settlementRecord"})
    Optional<ReconciliationItem> findWithDetailsById(UUID id);

    List<ReconciliationItem> findByTransactionId(UUID transactionId);

    List<ReconciliationItem> findBySettlementRecordId(UUID settlementRecordId);

    @Query("select i.status, count(i) from ReconciliationItem i group by i.status")
    List<Object[]> groupedStatusCounts();

    @Query("select i.reason, count(i) from ReconciliationItem i group by i.reason")
    List<Object[]> groupedReasonCounts();

    @Query("""
            select coalesce(sum(abs(i.transaction.amount - i.settlementRecord.amount)), 0)
            from ReconciliationItem i
            where i.status <> :resolved
              and i.reason = :reason
            """)
    BigDecimal sumUnresolvedAmountDelta(@Param("resolved") ReconciliationStatus resolved,
                                       @Param("reason") ReconciliationReason reason);
}
