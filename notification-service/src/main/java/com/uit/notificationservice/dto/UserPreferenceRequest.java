package com.uit.notificationservice.dto;

import jakarta.validation.constraints.Email;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPreferenceRequest {
    
    private String phoneNumber;
    
    @Email(message = "Invalid email format")
    private String email;
    
    private String deviceToken;
    
    private Boolean pushNotificationEnabled;
    
    private Boolean smsNotificationEnabled;
    
    private Boolean emailNotificationEnabled;
}
