package com.uit.transactionservice.client;

import com.uit.transactionservice.client.dto.RiskAssessmentRequest;
import com.uit.transactionservice.client.dto.RiskAssessmentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * Client for calling risk-engine service.
 * Used to assess transaction risk and determine required challenge type.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RiskEngineClient {

    private final WebClient.Builder webClientBuilder;

    @Value("${services.risk-engine.url:http://localhost:6000}")
    private String riskEngineUrl;

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    /**
     * Assess transaction risk and get required challenge type.
     * 
     * @param userId User initiating the transfer
     * @param amount Transaction amount
     * @param deviceFingerprint Client device fingerprint (optional)
     * @param ipAddress Client IP address (optional)
     * @param location Client location info (optional)
     * @return Risk assessment with level and challenge type
     */
    public RiskAssessmentResponse assessTransactionRisk(
            String userId,
            BigDecimal amount,
            String deviceFingerprint,
            String ipAddress,
            String location) {
        
        log.info("Assessing risk for userId: {} amount: {}", userId, amount);

        RiskAssessmentRequest request = RiskAssessmentRequest.builder()
                .userId(userId)
                .amount(amount)
                .deviceFingerprint(deviceFingerprint)
                .ipAddress(ipAddress)
                .location(location)
                .build();

        try {
            RiskAssessmentResponse response = webClientBuilder.build()
                    .post()
                    .uri(riskEngineUrl + "/assess")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(RiskAssessmentResponse.class)
                    .block(TIMEOUT);

            if (response != null) {
                log.info("Risk assessment result: level={} score={} challenge={}",
                        response.getRiskLevel(), response.getRiskScore(), response.getChallengeType());
                return response;
            }

            // Fallback: if null response, treat as medium risk (require SMS OTP)
            log.warn("Null response from risk-engine, defaulting to MEDIUM risk");
            return createDefaultResponse("MEDIUM", 50, "SMS_OTP");

        } catch (Exception e) {
            // Fail-safe: if risk-engine is unavailable, require SMS OTP (not NONE)
            log.error("Failed to assess risk: {}. Defaulting to MEDIUM risk with SMS_OTP", e.getMessage());
            return createDefaultResponse("MEDIUM", 50, "SMS_OTP");
        }
    }

    private RiskAssessmentResponse createDefaultResponse(String level, int score, String challengeType) {
        RiskAssessmentResponse response = new RiskAssessmentResponse();
        response.setRiskLevel(level);
        response.setRiskScore(score);
        response.setChallengeType(challengeType);
        return response;
    }
}
