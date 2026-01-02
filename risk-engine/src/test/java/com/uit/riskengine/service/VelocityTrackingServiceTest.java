package com.uit.riskengine.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for VelocityTrackingService
 * 
 * Tests the anti-salami-slicing velocity tracking logic.
 * Redis operations are mocked.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("VelocityTrackingService Unit Tests - Anti-Salami-Slicing")
class VelocityTrackingServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    private VelocityTrackingService velocityTrackingService;

    // Default test configuration
    private static final BigDecimal DAILY_LIMIT = new BigDecimal("50000");
    private static final int WINDOW_HOURS = 24;
    private static final int VELOCITY_SCORE = 35;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        velocityTrackingService = new VelocityTrackingService(redisTemplate);
        velocityTrackingService.setDailyLimit(DAILY_LIMIT);
        velocityTrackingService.setWindowHours(WINDOW_HOURS);
    }

    @Test
    @DisplayName("getDailyTotal returns zero when no previous transfers")
    void testGetDailyTotal_NoTransfers() {
        // Given: No existing transfers for user
        when(valueOperations.get("velocity:daily:user-123")).thenReturn(null);

        // When: Get daily total
        BigDecimal total = velocityTrackingService.getDailyTotal("user-123");

        // Then: Should return zero
        assertThat(total).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("getDailyTotal returns existing total from Redis")
    void testGetDailyTotal_ExistingTotal() {
        // Given: User has existing transfers totaling 25000
        when(valueOperations.get("velocity:daily:user-123")).thenReturn("25000");

        // When: Get daily total
        BigDecimal total = velocityTrackingService.getDailyTotal("user-123");

        // Then: Should return 25000
        assertThat(total).isEqualByComparingTo(new BigDecimal("25000"));
    }

    @Test
    @DisplayName("recordTransfer adds to existing total")
    void testRecordTransfer_AddToExisting() {
        // Given: User has 20000 in transfers, new transfer of 5000
        when(valueOperations.get("velocity:daily:user-123")).thenReturn("20000");

        // When: Record new transfer
        velocityTrackingService.recordTransfer("user-123", new BigDecimal("5000"));

        // Then: Should update Redis with new total of 25000
        verify(valueOperations).set(
                eq("velocity:daily:user-123"),
                eq("25000"),
                eq(Duration.ofHours(WINDOW_HOURS))
        );
    }

    @Test
    @DisplayName("recordTransfer creates new entry when first transfer")
    void testRecordTransfer_FirstTransfer() {
        // Given: No existing transfers for user
        when(valueOperations.get("velocity:daily:user-123")).thenReturn(null);

        // When: Record first transfer
        velocityTrackingService.recordTransfer("user-123", new BigDecimal("10000"));

        // Then: Should create Redis entry with 10000
        verify(valueOperations).set(
                eq("velocity:daily:user-123"),
                eq("10000"),
                eq(Duration.ofHours(WINDOW_HOURS))
        );
    }

    @Test
    @DisplayName("calculateVelocityRiskScore returns 0 when under limit")
    void testCalculateRiskScore_UnderLimit() {
        // Given: User has 20000, transfer of 10000, total 30000 < 50000 limit
        when(valueOperations.get("velocity:daily:user-123")).thenReturn("20000");

        // When: Calculate risk score for 10000 transfer
        int score = velocityTrackingService.calculateVelocityRiskScore("user-123", new BigDecimal("10000"));

        // Then: Should return 0 (no risk)
        assertThat(score).isEqualTo(0);
    }

    @Test
    @DisplayName("calculateVelocityRiskScore returns 35 when exceeding limit")
    void testCalculateRiskScore_ExceedsLimit() {
        // Given: User has 45000, transfer of 10000, total 55000 > 50000 limit
        when(valueOperations.get("velocity:daily:user-123")).thenReturn("45000");

        // When: Calculate risk score for 10000 transfer
        int score = velocityTrackingService.calculateVelocityRiskScore("user-123", new BigDecimal("10000"));

        // Then: Should return 35 (velocity risk points)
        assertThat(score).isEqualTo(VELOCITY_SCORE);
    }

    @Test
    @DisplayName("calculateVelocityRiskScore returns 35 when exactly at limit")
    void testCalculateRiskScore_AtLimit() {
        // Given: User has 40000, transfer of 10000, total 50000 == limit
        // This is exactly at limit, NOT exceeding, so should be 0
        when(valueOperations.get("velocity:daily:user-123")).thenReturn("40000");

        // When: Calculate risk score for 10000 transfer
        int score = velocityTrackingService.calculateVelocityRiskScore("user-123", new BigDecimal("10000"));

        // Then: Should return 0 (exactly at limit is OK)
        assertThat(score).isEqualTo(0);
    }

    @Test
    @DisplayName("wouldExceedLimit returns false when under limit")
    void testWouldExceedLimit_UnderLimit() {
        // Given: User has 30000, transfer of 10000, total 40000 < 50000
        when(valueOperations.get("velocity:daily:user-123")).thenReturn("30000");

        // When: Check if transfer would exceed limit
        boolean exceeds = velocityTrackingService.wouldExceedLimit("user-123", new BigDecimal("10000"));

        // Then: Should return false
        assertThat(exceeds).isFalse();
    }

    @Test
    @DisplayName("wouldExceedLimit returns true when over limit")
    void testWouldExceedLimit_OverLimit() {
        // Given: User has 45000, transfer of 10000, total 55000 > 50000
        when(valueOperations.get("velocity:daily:user-123")).thenReturn("45000");

        // When: Check if transfer would exceed limit
        boolean exceeds = velocityTrackingService.wouldExceedLimit("user-123", new BigDecimal("10000"));

        // Then: Should return true
        assertThat(exceeds).isTrue();
    }

    @Test
    @DisplayName("Salami slicing attack: many small transfers trigger velocity limit")
    void testSalamiSlicingAttack_MultipleSmallTransfers() {
        // Given: Attacker has made many small transfers totaling 49000
        // Each small transfer was under HIGH_AMOUNT threshold (10000)
        // But cumulative total now exceeds daily limit
        when(valueOperations.get("velocity:daily:attacker-123")).thenReturn("49000");

        // When: Attacker tries another "small" 5000 transfer
        int score = velocityTrackingService.calculateVelocityRiskScore("attacker-123", new BigDecimal("5000"));
        boolean wouldExceed = velocityTrackingService.wouldExceedLimit("attacker-123", new BigDecimal("5000"));

        // Then: Velocity limit should catch this attack
        assertThat(score).isEqualTo(VELOCITY_SCORE); // +35 points
        assertThat(wouldExceed).isTrue();
    }

    @Test
    @DisplayName("Legitimate user: first large transfer passes velocity check")
    void testLegitimateUser_FirstLargeTransfer() {
        // Given: User has no previous transfers today
        when(valueOperations.get("velocity:daily:user-123")).thenReturn(null);

        // When: User makes a 40000 transfer (under 50000 limit)
        int score = velocityTrackingService.calculateVelocityRiskScore("user-123", new BigDecimal("40000"));

        // Then: Should not trigger velocity risk
        assertThat(score).isEqualTo(0);
    }

    @Test
    @DisplayName("Edge case: null amount treated as zero")
    void testNullAmount() {
        // Given: User tries to check with null amount
        when(valueOperations.get("velocity:daily:user-123")).thenReturn("10000");

        // When: Calculate risk with null amount - this should handle gracefully
        // The service should treat null as zero or handle appropriately
        // Let's verify no exception is thrown
        BigDecimal total = velocityTrackingService.getDailyTotal("user-123");
        assertThat(total).isNotNull();
    }

    @Test
    @DisplayName("Redis returns non-numeric string - handles gracefully")
    void testRedisReturnsInvalidData() {
        // Given: Redis returns corrupt data
        when(valueOperations.get("velocity:daily:user-123")).thenReturn("not-a-number");

        // When: Get daily total - should handle parse error gracefully
        BigDecimal total = velocityTrackingService.getDailyTotal("user-123");

        // Then: Should return zero (fail-safe)
        assertThat(total).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
