package com.uit.sharedkernel.notification;

import com.uit.sharedkernel.amqp.RabbitMQMessageProducer;
import com.uit.sharedkernel.constants.RabbitMQConstants;
import com.uit.sharedkernel.dto.NotificationEventDto;
import com.uit.sharedkernel.dto.OtpEventDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Helper component to publish notification events directly to RabbitMQ.
 * Used by services (Account, Transaction, etc.) to trigger notifications.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationEventPublisher {

    private final RabbitMQMessageProducer messageProducer;

    /**
     * Publish a generic notification event (Email/Push)
     * Target: Transaction Exchange (where Notification Service is listening)
     * Routing Key: notification.event (Matches 'notification.*' pattern)
     */
    public void publishMail(NotificationEventDto event) {
        try {
            // Ensure timestamp is present
            if (event.getTimestamp() == null) {
                event.setTimestamp(LocalDateTime.now().toString());
            }

            // Publish to TRANSACTION_EXCHANGE
            // Note: Notification Service binds its queue to TRANSACTION_EXCHANGE with key 'notification.*'
            messageProducer.publish(
                    event,
                    RabbitMQConstants.TRANSACTION_EXCHANGE,
                    RabbitMQConstants.TRANSACTION_SUCCESS
            );

            log.info("Notification event published for transaction: {}", event.getTransactionId());

        } catch (Exception e) {
            log.error("Failed to publish notification event for transaction: {}", event.getTransactionId(), e);
            // Non-blocking: Notification failures shouldn't revert the main transaction
        }
    }

    /**
     * Publish an OTP generation event (SMS)
     * Target: Transaction Exchange
     * Routing Key: otp.generated
     */
    public void publishOtp(OtpEventDto event) {
        try {
            messageProducer.publish(
                    event,
                    RabbitMQConstants.TRANSACTION_EXCHANGE,
                    RabbitMQConstants.OTP_ROUTING_KEY
            );
            log.info("OTP event published for transaction: {}", event.getTransactionId());
        } catch (Exception e) {
            log.error("Failed to publish OTP event for transaction: {}", event.getTransactionId(), e);
        }
    }
}
