package com.basilcode.payment_reconciliation_engine.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "paystack")
public record PaystackProperties(String secretKey, String baseUrl) {
}