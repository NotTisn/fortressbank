package com.uit.notificationservice.listener;

import com.uit.notificationservice.dto.EmailNotificationRequest;
import com.uit.notificationservice.entity.UserPreference;
import com.uit.notificationservice.repository.UserPreferenceRepo;
import com.uit.notificationservice.service.NotificationService;
import com.uit.sharedkernel.constants.RabbitMQConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationListener {
    private final NotificationService notificationService;
    private final UserPreferenceRepo userPreferenceRepo;

    /**
     * Handles OTP generation events from transaction-service
     * Queue: notification.otp.queue
     * Routing Key: otp.generated
     * Purpose: Send OTP via SMS to user's phone number
     */
    @RabbitListener(queues = RabbitMQConstants.OTP_QUEUE)
    public void handleOtpNotification(Map<String, Object> message) {
        log.info("Received OTP notification event: {}", message);

        try {
            String transactionId = (String) message.get("transactionId");
            String phoneNumber = (String) message.get("phoneNumber");
            String otpCode = (String) message.get("otpCode");

            log.info("Processing OTP notification for transaction: {}, phone: {}", transactionId, phoneNumber);

            // Send SMS OTP via TextBee API
            notificationService.sendSmsOtp(phoneNumber, otpCode);

            log.info("OTP SMS sent successfully for transaction: {}", transactionId);

        } catch (Exception e) {
            log.error("Failed to send OTP notification: {}", e.getMessage(), e);
            // OTP failures are critical - consider implementing retry logic or alerting
        }
    }

    /**
     * Handles Forgot Password OTP events from user-service
     * Queue: notification.forgot-password-otp.queue
     * Routing Key: forgot-password.otp
     * Purpose: Send OTP via SMS for password reset
     */
    @RabbitListener(queues = RabbitMQConstants.FORGOT_PASSWORD_OTP_QUEUE)
    public void handleForgotPasswordOtpNotification(Map<String, Object> message) {
        log.info("Received Forgot Password OTP notification event: {}", message);

        try {
            String phoneNumber = (String) message.get("phoneNumber");
            String otpCode = (String) message.get("otpCode");

            log.info("Processing Forgot Password OTP for phone: {}", phoneNumber);

            // Send SMS OTP via TextBee API
            notificationService.sendSmsOtp(phoneNumber, otpCode);

            log.info("Forgot Password OTP SMS sent successfully to: {}", phoneNumber);

        } catch (Exception e) {
            log.error("Failed to send Forgot Password OTP notification: {}", e.getMessage(), e);
            // OTP failures are critical - consider implementing retry logic or alerting
        }
    }

    /**
     * Handles Registration OTP events from user-service
     * Queue: notification.registration-otp.queue
     * Routing Key: registration.otp
     * Purpose: Send OTP via SMS for user registration
     */
    @RabbitListener(queues = RabbitMQConstants.REGISTRATION_OTP_QUEUE)
    public void handleRegistrationOtpNotification(Map<String, Object> message) {
        log.info("Received Registration OTP notification event: {}", message);

        try {
            String phoneNumber = (String) message.get("phoneNumber");
            String otpCode = (String) message.get("otpCode");

            log.info("Processing Registration OTP for phone: {}", phoneNumber);

            // Send SMS OTP via TextBee API
            notificationService.sendSmsOtp(phoneNumber, otpCode);

            log.info("Registration OTP SMS sent successfully to: {}", phoneNumber);

        } catch (Exception e) {
            log.error("Failed to send Registration OTP notification: {}", e.getMessage(), e);
            // OTP failures are critical - consider implementing retry logic or alerting
        }
    }

    /**
     * Handles Device Switch OTP events from user-service
     * Queue: notification.device-switch-otp.queue
     * Routing Key: device-switch.otp
     * Purpose: Send OTP via SMS for device switching authentication
     */
    @RabbitListener(queues = RabbitMQConstants.DEVICE_SWITCH_OTP_QUEUE)
    public void handleDeviceSwitchOtpNotification(Map<String, Object> message) {
        log.info("Received Device Switch OTP notification event: {}", message);

        try {
            String phoneNumber = (String) message.get("phoneNumber");
            String otpCode = (String) message.get("otpCode");
            String userId = (String) message.get("userId");

            log.info("Processing Device Switch OTP for user: {}, phone: {}", userId, phoneNumber);

            // Send SMS OTP via TextBee API
            notificationService.sendSmsOtp(phoneNumber, otpCode);

            log.info("Device Switch OTP SMS sent successfully to user: {}", userId);

        } catch (Exception e) {
            log.error("Failed to send Device Switch OTP notification: {}", e.getMessage(), e);
            // OTP failures are critical - consider implementing retry logic or alerting
        }
    }

    /**
     * Handles transaction completion/failure events from transaction-service
     * Queue: notification-queue
     * Routing Keys: notification.TransactionCompleted, notification.TransactionFailed, notification.ExternalTransferInitiated
     * Purpose: Send multi-channel notifications (Push, SMS, Email) based on user preferences
     */
    @RabbitListener(queues = RabbitMQConstants.TRANSACTION_QUEUE)
    public void handleTransactionNotification(Map<String, Object> message) {
        log.info("Received transaction notification event: {}", message);

        try {
            // Extract transaction details from message payload (matching transaction-service sender)
            String transactionId = (String) message.get("transactionId");
            String senderUserId = (String) message.get("senderUserId");
            String senderAccountNumber = (String) message.get("senderAccountNumber");
            String receiverUserId = (String) message.get("receiverUserId");
            String receiverAccountNumber = (String) message.get("receiverAccountNumber");
            Integer notiWho = (Integer) message.get("notiWho"); // 0: both, 1: only sender, 2: only receiver
            Object amountObj = message.get("amount");
            String status = (String) message.get("status");
            boolean success = Boolean.TRUE.equals(message.get("success")); // Safe unboxing
            String notificationMessage = (String) message.get("message");

            // Convert amount to BigDecimal
            BigDecimal amount = null;
            if (amountObj instanceof Number) {
                amount = new BigDecimal(amountObj.toString());
            }

            log.info("Processing transaction notification - TxID: {}, SenderUser: {}, SenderAccount: {}, ReceiverUser: {}, ReceiverAccount: {}, Status: {}, Success: {}",
                    transactionId, senderUserId, senderAccountNumber, receiverUserId, receiverAccountNumber, status, success);

            // ========== SENDER NOTIFICATION (Money Deducted) ==========
            log.info("Processing sender notification for user: {} (account: {})", senderUserId, senderAccountNumber);
            if (notiWho == 0 || notiWho == 1) {
                UserPreference senderPreference = userPreferenceRepo.findById(senderUserId)
                        .orElseGet(() -> {
                            log.warn("User preference not found for sender user: {}, using defaults", senderUserId);
                            return createDefaultPreference(senderUserId);
                        });

                // Build sender notification (money deducted)
                String senderTitle = success ? "Money Sent Successfully" : "Transaction Failed";
                String senderContent = formatSenderNotification(success, amount, receiverAccountNumber, status, notificationMessage);

                // Send Push Notification to Sender
                sendPushNotification(senderUserId, senderPreference, transactionId, senderTitle, senderContent);

                // Send Email Notification to Sender
                sendEmailNotification(senderUserId, senderPreference, transactionId, senderTitle,
                        senderContent, status, amount, success, "Recipient", receiverAccountNumber);
            }

            // ========== RECEIVER NOTIFICATION (Money Received) ==========
            // Only send to receiver if transaction is successful
            if (success && notiWho == 0 || notiWho == 2) {
                log.info("Processing receiver notification for user: {} (account: {})", receiverUserId, receiverAccountNumber);

                UserPreference receiverPreference = userPreferenceRepo.findById(receiverUserId)
                        .orElseGet(() -> {
                            log.warn("User preference not found for receiver user: {}, using defaults", receiverUserId);
                            return createDefaultPreference(receiverUserId);
                        });

                // Build receiver notification (money received)
                String receiverTitle = "Money Received";
                String receiverContent = formatReceiverNotification(amount, senderAccountNumber);

                // Send Push Notification to Receiver
                sendPushNotification(receiverUserId, receiverPreference, transactionId, receiverTitle, receiverContent);

                // Send Email Notification to Receiver
                sendEmailNotification(receiverUserId, receiverPreference, transactionId, receiverTitle,
                        receiverContent, status, amount, true, "Sender", senderAccountNumber);
            }

            log.info("Transaction notification processing completed for: {}", transactionId);

        } catch (Exception e) {
            log.error("Critical error processing transaction notification: {}", e.getMessage(), e);
            // Don't throw exception - notifications are non-critical, avoid message requeue loops
        }
    }

    /**
     * Format sender notification message (money deducted)
     */
    private String formatSenderNotification(boolean success, BigDecimal amount,
                                            String receiverAccountNumber, String status, String message) {
        StringBuilder sb = new StringBuilder();

        if (success) {
            sb.append("You have successfully sent money.\n");
            if (amount != null) {
                sb.append("Amount: -").append(amount).append(" VND\n");
            }
            sb.append("To: ").append(receiverAccountNumber).append("\n");
        } else {
            sb.append("Your transaction has failed.\n");
            if (amount != null) {
                sb.append("Amount: ").append(amount).append(" VND\n");
            }
        }

        sb.append("Status: ").append(status).append("\n");

        if (message != null && !message.isEmpty()) {
            sb.append("Details: ").append(message);
        }

        return sb.toString();
    }

    /**
     * Format receiver notification message (money received)
     */
    private String formatReceiverNotification(BigDecimal amount, String senderAccountNumber) {
        StringBuilder sb = new StringBuilder();

        sb.append("You have received money!\n");
        if (amount != null) {
            sb.append("Amount: +").append(amount).append(" VND\n");
        }
        sb.append("From: ").append(senderAccountNumber).append("\n");
        sb.append("Status: COMPLETED\n");

        return sb.toString();
    }

    /**
     * Send push notification to user
     */
    private void sendPushNotification(String userId, UserPreference userPreference,
                                      String transactionId, String title, String content) {
        if (userPreference.isPushNotificationEnabled() &&
                userPreference.getDeviceToken() != null &&
                !userPreference.getDeviceToken().isEmpty()) {

            try {
                notificationService.sendTransactionNotification(
                        userId,
                        userPreference.getDeviceToken(),
                        title,
                        content
                );
                log.info("Push notification sent for transaction: {} to user: {} ({} devices)",
                        transactionId, userId, userPreference.getDeviceToken());
            } catch (Exception e) {
                log.error("Failed to send push notification for transaction {} to user {}: {}",
                        transactionId, userId, e.getMessage());
            }
        } else {
            log.info("Push notification not sent for user: {} - disabled or no device tokens", userId);
        }
    }

    /**
     * Send email notification to user
     */
    private void sendEmailNotification(String userId, UserPreference userPreference,
                                       String transactionId, String title, String content,
                                       String status, BigDecimal amount, boolean success,
                                       String counterPartyLabel, String counterPartyAccountNumber) {
        if (userPreference.isEmailNotificationEnabled() &&
                userPreference.getEmail() != null &&
                !userPreference.getEmail().isEmpty()) {

            try {
                // Build additional info for email
                List<EmailNotificationRequest.InfoRow> additionalInfo = new ArrayList<>();
                additionalInfo.add(EmailNotificationRequest.InfoRow.builder()
                        .label("Transaction ID: ")
                        .value(transactionId.substring(0, Math.min(16, transactionId.length())))
                        .build());
                additionalInfo.add(EmailNotificationRequest.InfoRow.builder()
                        .label("Amount: ")
                        .value(amount != null ? amount.toString() + " VND" : "N/A")
                        .build());
                additionalInfo.add(EmailNotificationRequest.InfoRow.builder()
                        .label(counterPartyLabel + ": ")
                        .value(counterPartyAccountNumber)
                        .build());
                additionalInfo.add(EmailNotificationRequest.InfoRow.builder()
                        .label("Status: ")
                        .value(status)
                        .build());

                String badge = success ? "SUCCESS" : "FAILED";

                notificationService.sendTransactionEmail(
                        userPreference.getEmail(),
                        title,
                        content,
                        badge,
                        additionalInfo
                );

                log.info("Email notification sent for transaction: {} to user: {} ({})",
                        transactionId, userId, userPreference.getEmail());
            } catch (Exception e) {
                log.error("Failed to send email notification for transaction {} to user {}: {}",
                        transactionId, userId, e.getMessage());
            }
        }
    }

    /**
     * Format transaction notification message (legacy - kept for compatibility)
     */
    private String formatTransactionNotification(boolean success, BigDecimal amount,
                                                 String status, String message) {
        StringBuilder sb = new StringBuilder();

        if (success) {
            sb.append("Your transaction has been completed successfully.\n");
        } else {
            sb.append("Your transaction has failed.\n");
        }

        if (amount != null) {
            sb.append("Amount: ").append(amount).append("\n");
        }

        sb.append("Status: ").append(status).append("\n");

        if (message != null && !message.isEmpty()) {
            sb.append("Details: ").append(message);
        }

        return sb.toString();
    }

    /**
     * Create default user preference if not found
     * Saves to database for future use
     */
    private UserPreference createDefaultPreference(String userId) {
        log.info("Creating and saving default preference for user: {}", userId);

        UserPreference defaultPref = new UserPreference();
        defaultPref.setUserId(userId);
        defaultPref.setPushNotificationEnabled(true);
        defaultPref.setSmsNotificationEnabled(false);
        defaultPref.setEmailNotificationEnabled(false);

        // Save to database so it persists
        return userPreferenceRepo.save(defaultPref);
    }
}
