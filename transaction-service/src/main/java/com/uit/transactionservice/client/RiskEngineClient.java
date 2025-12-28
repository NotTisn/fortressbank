package com.uit.transactionservice.client;

import com.uit.transactionservice.client.dto.RiskAssessmentRequest;
import com.uit.transactionservice.client.dto.RiskAssessmentResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "risk-engine", url = "${services.risk-engine.url:http://localhost:6000}")
public interface RiskEngineClient {

    @PostMapping("/assess")
    RiskAssessmentResponse assessRisk(@RequestBody RiskAssessmentRequest request);
}
