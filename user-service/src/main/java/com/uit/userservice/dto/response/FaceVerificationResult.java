package com.uit.userservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FaceVerificationResult {
    private boolean verified;
    private String message;
    private String failureReason;
    private Double similarity;
    private Double livenessScore;
}
