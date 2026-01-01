package com.uit.notificationservice.service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import com.uit.notificationservice.dto.SendNotificationRequest;
import com.uit.notificationservice.entity.NotificationMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class FirebaseMessagingService {
    private final FirebaseMessaging firebaseMessaging;

    public void sendNotification(String deviceToken, SendNotificationRequest request) throws FirebaseMessagingException {
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
