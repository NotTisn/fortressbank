package com.uit.riskengine.service;

import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * Velocity Tracking Service - Anti-Salami Slicing Protection
 * 
 * Tracks cumulative transfer amounts per user over a 24-hour sliding window.
 * Prevents attackers from bypassing HIGH_AMOUNT_THRESHOLD by splitting
 * large transfers into many small ones.
 * 
 * Attack Example:
 * - HIGH_AMOUNT_THRESHOLD = 10,000 VND (triggers +40 risk score)
 * - Attacker sends 5 x 9,900 VND transfers = 49,500 VND total
 * - Without velocity tracking: Each transfer scores 0 (LOW risk)
 * - With velocity tracking: 6th transfer would exceed daily limit
 * 
 * Redis Key Format: velocity:daily:{userId}
 * TTL: 24 hours (auto-cleanup)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VelocityTrackingService {

    private static final String VELOCITY_KEY_PREFIX = "velocity:daily:";

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${risk.velocity.daily-limit:50000}")
    @Setter
    private BigDecimal dailyLimit = new BigDecimal("50000");

    @Value("${risk.velocity.window-hours:24}")
    @Setter
    private int windowHours = 24;

    /**
     * Get the current cumulative daily transfer amount for a user.
     * 
     * @param userId The user ID
     * @return Cumulative amount in the current 24h window
     */
    public BigDecimal getDailyTotal(String userId) {
        String key = VELOCITY_KEY_PREFIX + userId;
        try {
            Object value = redisTemplate.opsForValue().get(key);
            if (value == null) {
                return BigDecimal.ZERO;
            }
            if (value instanceof Number) {
                return BigDecimal.valueOf(((Number) value).doubleValue());
            }
            return new BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            log.warn("Invalid velocity data for user {}: not a number", userId);
            return BigDecimal.ZERO;
        } catch (Exception e) {
            log.warn("Failed to get velocity data for user {}: {}", userId, e.getMessage());
            // Fail-open for availability, but log for monitoring
            return BigDecimal.ZERO;
        }
    }

    /**
     * Record a transfer amount for velocity tracking.
     * This should be called AFTER a successful transfer.
     * 
     * @param userId The user ID
     * @param amount The transfer amount
     * @return The new cumulative total after this transfer
     */
    public BigDecimal recordTransfer(String userId, BigDecimal amount) {
        String key = VELOCITY_KEY_PREFIX + userId;
        Duration window = Duration.ofHours(windowHours);
        
        try {
            BigDecimal currentTotal = getDailyTotal(userId);
            BigDecimal newTotal = currentTotal.add(amount);
            
            // Store as String for reliable serialization/deserialization
            redisTemplate.opsForValue().set(key, newTotal.toPlainString(), window);
            
            log.debug("Recorded velocity for user {}: {} -> {} (limit: {})", 
                    userId, currentTotal, newTotal, dailyLimit);
            
            return newTotal;
        } catch (Exception e) {
            log.error("Failed to record velocity for user {}: {}", userId, e.getMessage());
            // Fail-open for availability
            return amount;
        }
    }

    /**
     * Calculate risk score based on cumulative daily velocity.
     * 
     * @param userId The user ID
     * @param proposedAmount The amount of the proposed transfer
     * @return Risk score (0 if under limit, 35 if would exceed limit)
     */
    public int calculateVelocityRiskScore(String userId, BigDecimal proposedAmount) {
        BigDecimal currentTotal = getDailyTotal(userId);
        BigDecimal projectedTotal = currentTotal.add(proposedAmount);
        
        if (projectedTotal.compareTo(dailyLimit) > 0) {
            log.info("Velocity limit exceeded for user {}: {} + {} = {} > {}", 
                    userId, currentTotal, proposedAmount, projectedTotal, dailyLimit);
            return 35; // Significant risk score - directly triggers MEDIUM risk
        }
        
        // Warn if approaching limit (80%)
        BigDecimal threshold80 = dailyLimit.multiply(new BigDecimal("0.8"));
        if (projectedTotal.compareTo(threshold80) > 0) {
            log.info("Velocity approaching limit for user {}: {}% utilized", 
                    userId, projectedTotal.multiply(new BigDecimal("100")).divide(dailyLimit, 1, BigDecimal.ROUND_HALF_UP));
        }
        
        return 0;
    }

    /**
     * Get the configured daily limit.
     */
    public BigDecimal getDailyLimit() {
        return dailyLimit;
    }

    /**
     * Check if a transfer would exceed the daily limit.
     * Useful for pre-validation before attempting transfer.
     */
    public boolean wouldExceedLimit(String userId, BigDecimal proposedAmount) {
        BigDecimal currentTotal = getDailyTotal(userId);
        return currentTotal.add(proposedAmount).compareTo(dailyLimit) > 0;
    }
}
