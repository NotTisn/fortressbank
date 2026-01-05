package com.uit.transactionservice.client;

import com.uit.sharedkernel.api.ApiResponse;
import com.uit.transactionservice.client.dto.UserResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Feign Client for User Service
 * Used for inter-service communication with user-service
 */
@FeignClient(name = "user-service", url = "http://user-service:4000")
public interface UserServiceFeignClient {

    /**
     * Get user information by userId
     * @param userId The user ID
     * @return ApiResponse containing UserResponse with user details including phoneNumber
     */
    @GetMapping("/users/internal/{userId}")
    ApiResponse<UserResponse> getUserById(@PathVariable("userId") String userId);
}
