package com.uit.riskengine.service;

import com.uit.riskengine.client.UserRiskProfileClient;
import com.uit.riskengine.dto.RiskAssessmentRequest;
import com.uit.riskengine.dto.RiskAssessmentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for RiskEngineService
 * Matches the current simplified logic:
 * - HIGH risk if amount >= 1000 or unknown device.
 * - LOW risk otherwise.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RiskEngineService Unit Tests")
class RiskEngineServiceTest {

    @Mock
    private UserRiskProfileClient userRiskProfileClient;

    @InjectMocks
    private RiskEngineService riskEngineService;

    private UserRiskProfileClient.UserRiskProfileData lowRiskProfile;

    @BeforeEach
    void setUp() {
        lowRiskProfile = new UserRiskProfileClient.UserRiskProfileData();
        lowRiskProfile.setKnownDevices(Arrays.asList("device-123", "device-456"));
    }

    @Test
    @DisplayName("Low-risk transaction: small amount and known device")
    void testLowRiskTransaction() {
        RiskAssessmentRequest request = RiskAssessmentRequest.builder()
                .amount(BigDecimal.valueOf(100.00))
                .userId("user-123")
                .deviceFingerprint("device-123")
                .build();

        when(userRiskProfileClient.getUserRiskProfile("user-123"))
                .thenReturn(lowRiskProfile);

        RiskAssessmentResponse response = riskEngineService.assessRisk(request);

        assertThat(response.getRiskLevel()).isEqualTo("LOW");
        assertThat(response.getChallengeType()).isEqualTo("NONE");
    }

    @Test
    @DisplayName("High-risk transaction: high amount threshold exceeded")
    void testHighRiskTransaction_HighAmount() {
        RiskAssessmentRequest request = RiskAssessmentRequest.builder()
                .amount(BigDecimal.valueOf(1500.00))
                .userId("user-123")
                .deviceFingerprint("device-123")
                .build();

        when(userRiskProfileClient.getUserRiskProfile("user-123"))
                .thenReturn(lowRiskProfile);

        RiskAssessmentResponse response = riskEngineService.assessRisk(request);

        assertThat(response.getRiskLevel()).isEqualTo("HIGH");
        assertThat(response.getChallengeType()).isEqualTo("FACE_ID");
    }

    @Test
    @DisplayName("High-risk transaction: unknown device")
    void testHighRiskTransaction_UnknownDevice() {
        RiskAssessmentRequest request = RiskAssessmentRequest.builder()
                .amount(BigDecimal.valueOf(500.00))
                .userId("user-123")
                .deviceFingerprint("unknown-device")
                .build();

        when(userRiskProfileClient.getUserRiskProfile("user-123"))
                .thenReturn(lowRiskProfile);

        RiskAssessmentResponse response = riskEngineService.assessRisk(request);

        assertThat(response.getRiskLevel()).isEqualTo("HIGH");
        assertThat(response.getChallengeType()).isEqualTo("FACE_ID");
    }

    @Test
    @DisplayName("Edge case: null device fingerprint")
    void testNullDeviceFingerprint() {
        RiskAssessmentRequest request = RiskAssessmentRequest.builder()
                .amount(BigDecimal.valueOf(500.00))
                .userId("user-123")
                .deviceFingerprint(null)
                .build();

        when(userRiskProfileClient.getUserRiskProfile("user-123"))
                .thenReturn(lowRiskProfile);

        RiskAssessmentResponse response = riskEngineService.assessRisk(request);

        assertThat(response.getRiskLevel()).isEqualTo("LOW");
    }
}