package com.uit.userservice.dto.response.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class FaceRegisterResponse {
    private String status;
    private String message;
    private String reason;

    private List<Double> vector;

    @JsonProperty("avg_liveness_score")
    private Double avgLivenessScore;

    @JsonProperty("liveness_score")
    private Double livenessScore;

    @JsonProperty("sample_size")
    private Integer sampleSize;

    public boolean isSuccess() {
        return "success".equalsIgnoreCase(status);
    }
}
