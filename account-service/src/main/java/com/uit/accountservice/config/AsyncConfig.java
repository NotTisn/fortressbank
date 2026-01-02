package com.uit.accountservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Configuration to enable async processing.
 * Used for non-blocking operations like velocity recording
 * that shouldn't slow down the main transaction flow.
 */
@Configuration
@EnableAsync
public class AsyncConfig {
    // Spring will automatically configure a default TaskExecutor for @Async methods
}
