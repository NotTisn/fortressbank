package com.uit.notificationservice.service;

import com.uit.notificationservice.dto.UserPreferenceRequest;
import com.uit.notificationservice.dto.UserPreferenceResponse;
import com.uit.notificationservice.entity.UserPreference;
import com.uit.notificationservice.repository.UserPreferenceRepo;
import com.uit.sharedkernel.exception.AppException;
import com.uit.sharedkernel.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserPreferenceService {

    private final UserPreferenceRepo userPreferenceRepo;

    /**
     * Get user preference by userId
     */
    public UserPreferenceResponse getUserPreference(String userId) {
        log.info("Getting user preference for user: {}", userId);
        
        UserPreference preference = userPreferenceRepo.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_PREFERENCE_NOT_FOUND));
        
        return mapToResponse(preference);
    }

    /**
     * Get all user preferences
     */
    public List<UserPreferenceResponse> getAllUserPreferences() {
        log.info("Getting all user preferences");
        
        List<UserPreference> preferences = userPreferenceRepo.findAll();
        return preferences.stream()
                .map(this::mapToResponse)
                .toList();
    }

    /**
     * Create user preference
     */
    @Transactional
    public UserPreferenceResponse createOrUpdateUserPreference(String userId, UserPreferenceRequest request) {
        log.info("Creating user preference for user: {}", userId);
        
        UserPreference preference = userPreferenceRepo.findById(userId)
                .orElseGet(() -> {
                    log.info("Creating new user preference for user: {}", userId);
                    UserPreference newPref = new UserPreference();
                    newPref.setUserId(userId);
                    newPref.setPushNotificationEnabled(true);
                    newPref.setSmsNotificationEnabled(true);
                    newPref.setEmailNotificationEnabled(true);
                    return newPref;
                });

        if (request.getPhoneNumber() != null) {
            preference.setPhoneNumber(request.getPhoneNumber());
        }
        
        if (request.getEmail() != null) {
            preference.setEmail(request.getEmail());
        }
        
        if (request.getDeviceToken() != null) {
            preference.setDeviceToken(request.getDeviceToken());
        }
        
        if (request.getPushNotificationEnabled() != null) {
            preference.setPushNotificationEnabled(request.getPushNotificationEnabled());
        }
        
        if (request.getSmsNotificationEnabled() != null) {
            preference.setSmsNotificationEnabled(request.getSmsNotificationEnabled());
        }
        
        if (request.getEmailNotificationEnabled() != null) {
            preference.setEmailNotificationEnabled(request.getEmailNotificationEnabled());
        }

        preference = userPreferenceRepo.save(preference);
        log.info("User preference saved for user: {}", userId);
        
        return mapToResponse(preference);
    }

    /**
     * Map entity to response DTO
     */
    private UserPreferenceResponse mapToResponse(UserPreference preference) {
        return UserPreferenceResponse.builder()
                .userId(preference.getUserId())
                .phoneNumber(preference.getPhoneNumber())
                .email(preference.getEmail())
                .deviceToken(preference.getDeviceToken())
                .pushNotificationEnabled(preference.isPushNotificationEnabled())
                .smsNotificationEnabled(preference.isSmsNotificationEnabled())
                .emailNotificationEnabled(preference.isEmailNotificationEnabled())
                .build();
    }
}
