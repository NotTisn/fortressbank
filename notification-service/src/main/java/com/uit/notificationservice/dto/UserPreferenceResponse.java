package com.uit.notificationservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPreferenceResponse {

    private String userId;

    private String phoneNumber;

    private String email;

    private String deviceToken;

    private boolean pushNotificationEnabled;

    private boolean smsNotificationEnabled;

    private boolean emailNotificationEnabled;
}
