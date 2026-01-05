package com.uit.transactionservice.config;

import com.stripe.Stripe;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Stripe Configuration
 * Initializes Stripe API key on application startup
 */
@Configuration
@Slf4j
public class StripeConfig {

    @Value("${stripe.api-key}")
    private String apiKey;

    @PostConstruct
    public void init() {
        if (apiKey != null && !apiKey.isEmpty()) {
            Stripe.apiKey = apiKey;
            log.info("Stripe API initialized successfully");
        } else {
            log.warn("Stripe API key not found. External lookups will fail.");
        }
    }
}
