package com.uit.riskengine.service;

import com.uit.riskengine.client.UserRiskProfileClient;
import com.uit.riskengine.dto.RiskAssessmentRequest;
import com.uit.riskengine.dto.RiskAssessmentResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RiskEngineService {

    private static final double HIGH_AMOUNT_THRESHOLD = 1000.0; // 1,000.00 USD

    private final UserRiskProfileClient userRiskProfileClient;

    public RiskAssessmentResponse assessRisk(RiskAssessmentRequest request) {
        boolean isHighRisk = false;
        List<String> reasons = new ArrayList<>();

        // Fetch user risk profile for device check
        UserRiskProfileClient.UserRiskProfileData profile = 
                userRiskProfileClient.getUserRiskProfile(request.getUserId());

        // RULE 1: High Transaction Amount
        if (request.getAmount().doubleValue() >= HIGH_AMOUNT_THRESHOLD) {
            isHighRisk = true;
            reasons.add(String.format("Transaction amount (%.2f) exceeds threshold of %.2f.", 
                    request.getAmount(), HIGH_AMOUNT_THRESHOLD));
        }

        // RULE 2: New Device Check
        if (request.getDeviceFingerprint() != null && !request.getDeviceFingerprint().isEmpty()) {
            if (!profile.getKnownDevices().contains(request.getDeviceFingerprint())) {
                isHighRisk = true;
                reasons.add("Transaction from unknown device (fingerprint not recognized).");
            }
        }

        // Risk Level Determination
        String riskLevel;
        String challengeType;

        if (isHighRisk) {
            riskLevel = "HIGH";
            challengeType = "FACE_ID";
        } else {
            riskLevel = "LOW";
            challengeType = "NONE";
        }

        RiskAssessmentResponse response = new RiskAssessmentResponse();
        response.setRiskLevel(riskLevel);
        response.setChallengeType(challengeType);
        return response;
    }
}
