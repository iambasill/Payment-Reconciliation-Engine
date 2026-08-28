package com.basilcode.payment_reconciliation_engine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class PaymentReconciliationEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentReconciliationEngineApplication.class, args);
    }

}
