package com.uit.userservice.client;

import com.uit.sharedkernel.api.ApiResponse;
import com.uit.userservice.dto.request.ConfirmFaceAuthRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "transaction-service")
public interface TransactionClient {

    @PostMapping("/transactions/internal/face-auth-success")
    ApiResponse<Void> confirmFaceAuthSuccess(@RequestBody ConfirmFaceAuthRequest request);
}
