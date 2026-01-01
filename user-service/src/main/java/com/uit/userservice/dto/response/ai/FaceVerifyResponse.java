package com.uit.userservice.dto.response.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class FaceVerifyResponse {
    private String status;
    private String message;
    private String reason;

    private Boolean match;

    private Double similarity;

    @JsonProperty("avg_liveness_score")
    private Double avgLivenessScore;

    @JsonProperty("liveness_score")
    private Double livenessScore;

    public boolean isSuccess() {
        return "success".equalsIgnoreCase(status);
    }

    public boolean isMatch() {
        return Boolean.TRUE.equals(match);
    }
}
