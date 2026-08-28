package com.basilcode.payment_reconciliation_engine.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class PaystackClientConfig {

    @Bean
    public RestClient paystackRestClient(PaystackProperties props) {
        return RestClient.builder()
                .baseUrl(props.baseUrl())
                .defaultHeader("Authorization", "Bearer " + props.secretKey())
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
}