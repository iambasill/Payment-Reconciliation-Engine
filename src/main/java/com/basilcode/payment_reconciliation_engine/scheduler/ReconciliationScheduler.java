package com.basilcode.payment_reconciliation_engine.scheduler;

import com.basilcode.payment_reconciliation_engine.service.ReconciliationEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;


@Component
@Slf4j
@RequiredArgsConstructor
public class ReconciliationScheduler {

    private final ReconciliationEngine reconciliationEngine;

    @Scheduled(cron = "${reconciliation.reconcile-cron:0 0 2 * * *}",
               zone = "${reconciliation.zone:UTC}")
    public void runScheduledReconciliation() {
        log.info("Scheduled reconciliation pass triggered");
        try {
            ReconciliationEngine.PassResult result = reconciliationEngine.reconciliation();
            log.info("Scheduled reconciliation finished: {}", result);
        } catch (Exception e) {
            log.error("Scheduled reconciliation failed: {}", e.getMessage(), e);
        }
    }
}
