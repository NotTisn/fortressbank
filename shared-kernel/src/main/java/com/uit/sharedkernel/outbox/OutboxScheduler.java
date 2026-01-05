package com.uit.sharedkernel.outbox;

import com.uit.sharedkernel.amqp.RabbitMQMessageProducer;
import com.uit.sharedkernel.outbox.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "outbox.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxScheduler {

    private final OutboxEventRepository outboxEventRepository;
    private final RabbitMQMessageProducer rabbitMQMessageProducer;

    private static final int MAX_RETRY_COUNT = 3;
    private static final int RETRY_MINUTES_DELAY = 5;

    @Scheduled(fixedDelayString = "${outbox.scheduler.fixed-delay:5000}", initialDelayString = "${outbox.scheduler.initial-delay:10000}")
    @Transactional
    public void processOutboxEvents() {
        log.debug("Starting outbox scheduler job...");

        List<OutboxEvent> pendingEvents = outboxEventRepository.findByStatus(OutboxEventStatus.PENDING);
        List<OutboxEvent> failedEventsToRetry = outboxEventRepository
                .findByStatusAndCreatedAtBefore(OutboxEventStatus.FAILED, LocalDateTime.now().minusMinutes(RETRY_MINUTES_DELAY));

        if (pendingEvents.isEmpty() && failedEventsToRetry.isEmpty()) {
            log.debug("No outbox events to publish.");
            return;
        }

        log.info("Found {} PENDING and {} FAILED events to process.", pendingEvents.size(), failedEventsToRetry.size());

        Stream.concat(pendingEvents.stream(), failedEventsToRetry.stream())
                .filter(event -> event.getRetryCount() == null || event.getRetryCount() < MAX_RETRY_COUNT)
                .forEach(this::publishEvent);
    }

    private void publishEvent(OutboxEvent event) {
        try {
            log.info("Processing event ID: {}, Type: {}", event.getEventId(), event.getEventType());

            event.setStatus(OutboxEventStatus.PROCESSING);
            outboxEventRepository.save(event);

            rabbitMQMessageProducer.publish(
                    event.getPayload(),
                    event.getExchange(),
                    event.getRoutingKey()
            );

            event.setStatus(OutboxEventStatus.COMPLETED);
            event.setProcessedAt(LocalDateTime.now());
            outboxEventRepository.save(event);

            log.info("Successfully published event ID: {}", event.getEventId());

        } catch (Exception e) {
            log.error("Failed to publish event ID: {}. Error: {}", event.getEventId(), e.getMessage(), e);

            event.setStatus(OutboxEventStatus.FAILED);
            event.setRetryCount(event.getRetryCount() == null ? 1 : event.getRetryCount() + 1);
            event.setErrorMessage(e.getMessage());
            outboxEventRepository.save(event);

            if (event.getRetryCount() >= MAX_RETRY_COUNT) {
                log.error("Event ID: {} has exceeded max retry count. Manual intervention required.", event.getEventId());
            }
        }
    }
}
