package com.uit.notificationservice.controller;

import com.uit.notificationservice.dto.UserPreferenceRequest;
import com.uit.notificationservice.dto.UserPreferenceResponse;
import com.uit.notificationservice.service.UserPreferenceService;
import com.uit.sharedkernel.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/user-preferences")
@RequiredArgsConstructor
@Slf4j
public class UserPreferenceController {

    private final UserPreferenceService userPreferenceService;

    /**
     * Get user preference by userId
     * GET /user-preferences/{userId}
     */
    @GetMapping("/{userId}")
    public ResponseEntity<UserPreferenceResponse> getUserPreference(
            @PathVariable("userId") String userId) {
        
        log.info("GET request for user preference: {}", userId);
        UserPreferenceResponse response = userPreferenceService.getUserPreference(userId);
        return ResponseEntity.status(200).body(response);
    }

    /**
     * Get all user preferences (Admin endpoint)
     * GET /user-preferences
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<UserPreferenceResponse>>> getAllUserPreferences() {
        
        log.info("GET request for all user preferences");
        List<UserPreferenceResponse> responses = userPreferenceService.getAllUserPreferences();
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    /**
     * Create or update user preference
     * POST /user-preferences/{userId}
     */
    @PostMapping("/{userId}")
    public ResponseEntity<ApiResponse<UserPreferenceResponse>> createOrUpdateUserPreference(
            @PathVariable("userId") String userId,
            @Valid @RequestBody UserPreferenceRequest request) {
        
        log.info("POST request to create/update user preference for user: {}", userId);
        UserPreferenceResponse response = userPreferenceService.createOrUpdateUserPreference(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }
}
