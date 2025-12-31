package com.uit.notificationservice.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Date;

@Data
@Builder
public class SendNotificationResponse {
    private String notificationId;
    private String userId;
    private String title;
    private String content;
    private String image;
    private String type;
    private boolean read;
    private LocalDateTime sentAt;
    private LocalDateTime readAt;
}