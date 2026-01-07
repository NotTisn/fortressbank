package com.uit.riskengine.service;

import com.uit.riskengine.client.UserRiskProfileClient;
import com.uit.riskengine.dto.RiskAssessmentRequest;
import com.uit.riskengine.dto.RiskAssessmentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Risk Engine Service - 7-Rule Fraud Detection System
 * 
 * Implements comprehensive fraud detection with the following rules:
 * - Rule 1: High Transaction Amount (40 points)
 * - Rule 2: Unusual Time of Day (30 points)
 * - Rule 3: New Device (25 points)
 * - Rule 4: Geolocation Anomaly (20 points)
 * - Rule 5: New Payee (15 points)
 * - Rule 6: Multiple Risk Factors (10 points)
 * - Rule 7: Aggregate Daily Velocity (35 points) - Anti-Salami Slicing
 * 
 * Risk Thresholds:
 * - 0-39: LOW (no challenge)
 * - 40-69: MEDIUM (SMS_OTP required)
 * - 70+: HIGH (SMART_OTP required)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RiskEngineService {

    private static final double HIGH_AMOUNT_THRESHOLD = 10000.0;
    private static final int UNUSUAL_HOURS_START = 2; // 2:00 AM
    private static final int UNUSUAL_HOURS_END = 6;   // 6:00 AM

    private final UserRiskProfileClient userRiskProfileClient;
    private final VelocityTrackingService velocityTrackingService;
    private final Clock clock;

    public RiskAssessmentResponse assessRisk(RiskAssessmentRequest request) {
        int score = 0;
        List<String> reasons = new ArrayList<>();
        LocalTime currentTime = LocalTime.now(clock);
        int currentHour = currentTime.getHour();

        // Fetch user risk profile for enhanced checks
        UserRiskProfileClient.UserRiskProfileData profile = 
                userRiskProfileClient.getUserRiskProfile(request.getUserId());

        // ============================================================
        // RULE 1: High Transaction Amount
        // ============================================================
        if (request.getAmount().doubleValue() > HIGH_AMOUNT_THRESHOLD) {
            score += 40;
            reasons.add(String.format("Transaction amount (%.2f) exceeds threshold of %.2f.", 
                    request.getAmount(), HIGH_AMOUNT_THRESHOLD));
        }

        // ============================================================
        // RULE 2: Unusual Time of Day
        // ============================================================
        if (currentHour >= UNUSUAL_HOURS_START && currentHour < UNUSUAL_HOURS_END) {
            score += 30;
            reasons.add(String.format("Transaction occurred during unusual hours (%02d:00).", currentHour));
        }

        // ============================================================
        // RULE 3: New Device (NEW)
        // ============================================================
        if (request.getDeviceFingerprint() != null && !request.getDeviceFingerprint().isEmpty()) {
            if (!profile.getKnownDevices().contains(request.getDeviceFingerprint())) {
                score += 25;
                reasons.add("Transaction from unknown device (fingerprint not recognized).");
            }
        }

        // ============================================================
        // RULE 4: Geolocation Anomaly (NEW)
        // ============================================================
        if (request.getLocation() != null && !request.getLocation().isEmpty()) {
            if (!profile.getKnownLocations().contains(request.getLocation())) {
                score += 20;
                reasons.add(String.format("Transaction from unusual location: %s.", request.getLocation()));
            }
        }

        // ============================================================
        // RULE 5: New Payee (NEW)
        // ============================================================
        if (request.getPayeeId() != null && !request.getPayeeId().isEmpty()) {
            if (!profile.getKnownPayees().contains(request.getPayeeId())) {
                score += 15;
                reasons.add("Transaction to new/unknown payee (first-time recipient).");
            }
        }

        // ============================================================
        // RULE 6: Multiple Risk Factors Velocity Check
        // ============================================================
        if (reasons.size() >= 3) {
            score += 10;
            reasons.add("Multiple risk factors detected (velocity concern).");
        }

        // ============================================================
        // RULE 7: Aggregate Daily Velocity (ANTI-SALAMI SLICING)
        // ============================================================
        int velocityScore = velocityTrackingService.calculateVelocityRiskScore(
                request.getUserId(), request.getAmount());
        if (velocityScore > 0) {
            score += velocityScore;
            reasons.add(String.format(
                    "Daily transfer velocity exceeded limit (%.2f limit, cumulative would exceed).",
                    velocityTrackingService.getDailyLimit()));
        }

        // ============================================================
        // Risk Level Determination
        // ============================================================
        String riskLevel;
        String challengeType;

        if (score >= 70) {
            riskLevel = "HIGH";
            challengeType = "SMART_OTP";
        } else if (score >= 40) {
            riskLevel = "MEDIUM";
            challengeType = "SMS_OTP";
        } else {
            riskLevel = "LOW";
            challengeType = "NONE";
        }

        log.info("Risk assessment for user {}: score={}, level={}, reasons={}", 
                request.getUserId(), score, riskLevel, reasons.size());

        RiskAssessmentResponse response = new RiskAssessmentResponse();
        response.setRiskLevel(riskLevel);
        response.setChallengeType(challengeType);
        return response;
    }

    /**
     * Record a completed transfer for velocity tracking.
     * Should be called by account-service after successful transfer.
     */
    public void recordCompletedTransfer(String userId, java.math.BigDecimal amount) {
        velocityTrackingService.recordTransfer(userId, amount);
    }
}
