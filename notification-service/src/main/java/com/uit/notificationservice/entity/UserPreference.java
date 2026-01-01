package com.uit.notificationservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "user_preference")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserPreference {
    @Id
    @Column(name = "user_id", length = 36)
    private String userId;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "email")
    private String email;

    @Column(name = "device_token")
    private String deviceToken;

    @Column(nullable = false, name = "push_notification_enabled")
    private boolean pushNotificationEnabled;

    @Column(nullable = false, name = "sms_notification_enabled")
    private boolean smsNotificationEnabled;

    @Column(nullable = false, name = "email_notification_enabled")
    private boolean emailNotificationEnabled;
}
