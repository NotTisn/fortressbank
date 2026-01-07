package com.uit.accountservice.riskengine;

import com.uit.accountservice.riskengine.dto.RiskAssessmentRequest;
import com.uit.accountservice.riskengine.dto.RiskAssessmentResponse;
import com.uit.accountservice.riskengine.dto.VelocityRecordRequest;
import com.uit.accountservice.riskengine.dto.VelocityRecordResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * Client service for communicating with risk-engine.
 * Handles risk assessment and velocity tracking.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RiskEngineService {

    private final WebClient.Builder webClientBuilder;

    @Value("${services.risk-engine.url:http://risk-engine:6000}")
    private String riskEngineUrl;

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    /**
     * Assess risk for a transaction before it's executed.
     * This is a blocking call as we need the result to decide whether to proceed.
     */
    public RiskAssessmentResponse assessRisk(RiskAssessmentRequest request) {
        return webClientBuilder.build()
                .post()
                .uri(riskEngineUrl + "/assess")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(RiskAssessmentResponse.class)
                .timeout(TIMEOUT)
                .block();
    }

    /**
     * Record a completed transfer in velocity tracking.
     * Called AFTER a transfer successfully completes.
     * This is async/fire-and-forget - we don't want to slow down the response.
     * 
     * @param userId The user who sent the transfer
     * @param amount The transfer amount
     * @param transactionId For correlation in logs
     */
    @Async
    public void recordVelocityAsync(String userId, BigDecimal amount, String transactionId) {
        VelocityRecordRequest request = VelocityRecordRequest.builder()
                .userId(userId)
                .amount(amount)
                .transactionId(transactionId)
                .build();

        webClientBuilder.build()
                .post()
                .uri(riskEngineUrl + "/assess/internal/velocity/record")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(VelocityRecordResponse.class)
                .timeout(TIMEOUT)
                .doOnSuccess(response -> {
                    if (response != null && response.isSuccess()) {
                        log.info("Velocity recorded - User: {} Amount: {} NewDailyTotal: {} TxID: {}",
                                userId, amount, response.getNewDailyTotal(), transactionId);
                    } else {
                        log.warn("Velocity recording returned unsuccessful - User: {} TxID: {}",
                                userId, transactionId);
                    }
                })
                .doOnError(error -> {
                    log.error("Failed to record velocity - User: {} TxID: {} Error: {}",
                            userId, transactionId, error.getMessage());
                })
                .subscribe(); // Non-blocking subscribe
    }

    /**
     * Get current daily velocity total for a user.
     * Used for pre-transfer checks or dashboard display.
     */
    public Mono<BigDecimal> getDailyVelocity(String userId) {
        return webClientBuilder.build()
                .get()
                .uri(riskEngineUrl + "/assess/internal/velocity/" + userId)
                .retrieve()
                .bodyToMono(BigDecimal.class)
                .timeout(TIMEOUT)
                .onErrorReturn(BigDecimal.ZERO); // Fail-open: don't block transfers if velocity service is down
    }
}
