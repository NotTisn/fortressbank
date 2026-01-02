package com.uit.notificationservice.service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import com.uit.notificationservice.dto.SendNotificationRequest;
import com.uit.notificationservice.entity.NotificationMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
public class FirebaseMessagingService {
    private final FirebaseMessaging firebaseMessaging;
    
    @Autowired
    public FirebaseMessagingService(@Autowired(required = false) FirebaseMessaging firebaseMessaging) {
        this.firebaseMessaging = firebaseMessaging;
        if (firebaseMessaging == null) {
            log.warn("FirebaseMessaging is not configured. Push notifications will be disabled.");
        }
    }

    public void sendNotification(String deviceToken, SendNotificationRequest request) throws FirebaseMessagingException {
        if (firebaseMessaging == null) {
            log.warn("FirebaseMessaging not configured. Skipping push notification to device: {}", deviceToken);
            return;
        }
        
        Notification notification = Notification
                .builder()
                .setTitle(request.getTitle())
                .setBody(request.getContent())
                .setImage(request.getImage())
                .build();

        Message message = Message
                .builder()
                .setToken(deviceToken)
                .setNotification(notification)
                .build();

        CompletableFuture.runAsync(() -> {
            try {
                firebaseMessaging.send(message);
                log.info("Firebase messages sent successfully to {} devices", deviceToken);
            } catch (FirebaseMessagingException e) {
                log.error("Failed to send Firebase messages: {}", e.getMessage(), e);
            }
        });
    }
}
