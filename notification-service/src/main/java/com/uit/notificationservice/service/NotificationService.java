package com.uit.notificationservice.service;

import com.google.firebase.messaging.FirebaseMessagingException;
import com.uit.notificationservice.dto.EmailNotificationRequest;
import com.uit.notificationservice.dto.SendNotificationRequest;
import com.uit.notificationservice.dto.SendNotificationResponse;
import com.uit.notificationservice.dto.TextBeeRequest;
import com.uit.notificationservice.entity.NotificationMessage;
import com.uit.notificationservice.mapper.NotificationMessageMapper;
import com.uit.notificationservice.repository.NotificationRepo;
import com.uit.sharedkernel.exception.AppException;
import com.uit.sharedkernel.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationService {

    private final WebClient.Builder webClientBuilder;
    private final NotificationRepo notificationRepo;

    private final FirebaseMessagingService firebaseMessagingService;
    private final EmailService emailService;

    @Value("${textbee.api.key}")
    private String apiKey;

    @Value("${textbee.device.id}")
    private String deviceId;

    public void sendSmsOtp(String phoneNumber, String otpCode) {
        String url = "https://api.textbee.dev/api/v1/gateway/devices/" + deviceId + "/send-sms";

        TextBeeRequest request = new TextBeeRequest(new String[]{phoneNumber}, "Your FortressBank verification code is: " + otpCode);

        webClientBuilder.build()
                .post()
                .uri(url)
                .header("x-api-key", apiKey)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(Void.class)
                .subscribe(
                        success -> log.info("SMS OTP sent successfully to {}", phoneNumber),
                        error -> log.error("Failed to send SMS OTP to {}: {}", phoneNumber, error.getMessage())
                );
    }

    public List<NotificationMessage> getNotifications() {
        return notificationRepo.findAll();
    }

    public NotificationMessage markAsRead(String notificationId) {
        Optional<NotificationMessage> notificationMessage = notificationRepo.findById(notificationId);
        if (notificationMessage.isEmpty()) {
            throw new AppException(ErrorCode.NOTIF_NOT_FOUND);
        }

        NotificationMessage message = notificationMessage.get();

        message.setRead(true);
        message.setReadAt(LocalDateTime.now());

        notificationRepo.save(message);
        log.info("Message's read: {}", message.getReadAt());
        log.info("Message's read: {}", message.isRead());
        return message;
    }

    /**
     * Send push notification via Firebase Cloud Messaging
     */
    /**
     * Send push notification via Firebase Cloud Messaging (single device)
     */
    public void sendPushNotification(String deviceToken, String title, String body) {
        try {
            log.info("Sending push notification to token: {}", deviceToken);

            SendNotificationRequest request = SendNotificationRequest.builder()
                    .deviceToken(deviceToken)
                    .title(title)
                    .content(body)
                    .type("NOTIFICATION")
                    .build();

            firebaseMessagingService.sendNotification(deviceToken, request);
            log.info("Push notification sent successfully");

        } catch (Exception e) {
            log.error("Failed to send push notification: {}", e.getMessage(), e);
        }
    }

    /**
     * Send transaction notification to multiple devices with database persistence
     * Modern banking approach: Use push notifications instead of SMS
     */
    public void sendTransactionNotification(String userId, String deviceToken,
                                            String title, String content) {
        try {
            log.info("Sending transaction notification to user: {} on {} devices", userId, deviceToken);

            // Create and save notification to database for each device
            NotificationMessage notification = NotificationMessage.builder()
                    .userId(userId)
                    .title(title)
                    .content(content)
                    .type("TRANSACTION")
                    .deviceToken(deviceToken)
                    .read(false)
                    .sentAt(LocalDateTime.now())
                    .createdAt(LocalDateTime.now())
                    .build();

            notificationRepo.save(notification);
            log.debug("Transaction notification saved to database for user: {}", userId);

            // Send push notification via Firebase
            SendNotificationRequest request = SendNotificationRequest.builder()
                    .userId(userId)
                    .title(title)
                    .content(content)
                    .type("TRANSACTION")
                    .isRead(false)
                    .sentAt(LocalDateTime.now())
                    .build();

            firebaseMessagingService.sendNotification(deviceToken, request);
            log.info("Transaction notification sent successfully to {} devices", deviceToken);

        } catch (
                Exception e) {
            log.error("Failed to send transaction notification to user {}: {}", userId, e.getMessage(), e);
        }
    }

    /**
     * Send email notification with optional info and CTA
     *
     * @param recipientEmail Recipient email address
     * @param recipientName  Recipient name (optional)
     * @param title          Email subject and main title
     * @param content        Main message content
     */
    public void sendEmailNotification(String recipientEmail, String recipientName,
                                      String title, String content) {
        try {
            log.info("Sending email notification to: {}", recipientEmail);

            EmailNotificationRequest request = EmailNotificationRequest.builder()
                    .recipientEmail(recipientEmail)
                    .recipientName(recipientName)
                    .title(title)
                    .content(content)
                    .build();

            emailService.sendEmailNotification(request);
            log.info("Email notification sent successfully to: {}", recipientEmail);

        } catch (Exception e) {
            log.error("Failed to send email notification to {}: {}", recipientEmail, e.getMessage(), e);
        }
    }

    /**
     * Send transaction email notification with detailed info
     *
     * @param recipientEmail Recipient email address
     * @param title          Email title
     * @param content        Main content
     * @param badge          Badge type (SUCCESS, FAILED, INFO)
     * @param additionalInfo Additional transaction details
     */
    public void sendTransactionEmail(String recipientEmail, String title, String content,
                                     String badge, List<EmailNotificationRequest.InfoRow> additionalInfo) {
        try {
            log.info("Sending transaction email to: {}", recipientEmail);

            EmailNotificationRequest request = EmailNotificationRequest.builder()
                    .recipientEmail(recipientEmail)
                    .title(title)
                    .content(content)
                    .badge(badge)
                    .additionalInfo(additionalInfo)
                    .ctaUrl("https://fortressbank.com/transactions") // Link to transaction history
                    .ctaText("View Transaction History")
                    .build();

            emailService.sendEmailNotification(request);
            log.info("Transaction email sent successfully to: {}", recipientEmail);

        } catch (Exception e) {
            log.error("Failed to send transaction email to {}: {}", recipientEmail, e.getMessage(), e);
        }
    }

    public NotificationMessage createAndSendNotification(SendNotificationRequest request) throws FirebaseMessagingException {
        NotificationMessage newNotification = NotificationMessage.builder()
                .userId(request.getUserId())
                .title(request.getTitle())
                .content(request.getContent())
                .image(request.getImage())
                .type(request.getType())
                .deviceToken(request.getDeviceToken())
                .read(false)
                .sentAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .build();

        notificationRepo.save(newNotification);
        firebaseMessagingService.sendNotification(request.getDeviceToken(), request);

        return newNotification;
    }
}
