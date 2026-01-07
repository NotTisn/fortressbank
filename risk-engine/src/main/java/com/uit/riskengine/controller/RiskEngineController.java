package com.uit.riskengine.controller;

import com.uit.riskengine.dto.RiskAssessmentRequest;
import com.uit.riskengine.dto.RiskAssessmentResponse;
import com.uit.riskengine.dto.VelocityRecordRequest;
import com.uit.riskengine.dto.VelocityRecordResponse;
import com.uit.riskengine.service.RiskEngineService;
import com.uit.riskengine.service.VelocityTrackingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/assess")
@RequiredArgsConstructor
@Slf4j
public class RiskEngineController {

    private final RiskEngineService riskEngineService;
    private final VelocityTrackingService velocityTrackingService;

    @PostMapping
    public ResponseEntity<RiskAssessmentResponse> assessRisk(@RequestBody RiskAssessmentRequest request) {
        RiskAssessmentResponse response = riskEngineService.assessRisk(request);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @GetMapping("/health")
    public ResponseEntity<String> healthCheck() {
        return new ResponseEntity<>("UP", HttpStatus.OK);
    }
    
    /**
     * Internal endpoint for recording completed transfers.
     * Called by account-service after a successful transfer completes.
     * This updates the user's daily velocity total for anti-salami-slicing protection.
     * 
     * SECURITY: This is an internal endpoint - should only be callable from Docker network.
     * Kong should NOT expose this route.
     */
    @PostMapping("/internal/velocity/record")
    public ResponseEntity<VelocityRecordResponse> recordTransferVelocity(
            @RequestBody VelocityRecordRequest request) {
        
        log.info("Recording velocity for user={}, amount={}, txId={}",
                request.getUserId(), request.getAmount(), request.getTransactionId());
        
        try {
            // Record the transfer in velocity tracking
            velocityTrackingService.recordTransfer(request.getUserId(), request.getAmount());
            
            // Get updated daily total
            BigDecimal newTotal = velocityTrackingService.getDailyTotal(request.getUserId());
            
            VelocityRecordResponse response = VelocityRecordResponse.builder()
                    .userId(request.getUserId())
                    .recordedAmount(request.getAmount())
                    .newDailyTotal(newTotal)
                    .success(true)
                    .message("Transfer recorded in velocity tracking")
                    .build();
            
            log.info("Velocity recorded. User {} daily total: {}", 
                    request.getUserId(), newTotal);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Failed to record velocity for user={}: {}", 
                    request.getUserId(), e.getMessage(), e);
            
            VelocityRecordResponse errorResponse = VelocityRecordResponse.builder()
                    .userId(request.getUserId())
                    .recordedAmount(request.getAmount())
                    .success(false)
                    .message("Failed to record velocity: " + e.getMessage())
                    .build();
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    
    /**
     * Internal endpoint to check user's current daily velocity total.
     * Useful for debugging and monitoring.
     */
    @GetMapping("/internal/velocity/{userId}")
    public ResponseEntity<BigDecimal> getDailyVelocity(@PathVariable("userId") String userId) {
        BigDecimal dailyTotal = velocityTrackingService.getDailyTotal(userId);
        return ResponseEntity.ok(dailyTotal);
    }
}
