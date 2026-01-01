package com.uit.userservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FaceRegistrationResult {
    private boolean success;
    private String message;
    private String failureReason;
    private Double livenessScore;
    private Integer sampleSize;
}
