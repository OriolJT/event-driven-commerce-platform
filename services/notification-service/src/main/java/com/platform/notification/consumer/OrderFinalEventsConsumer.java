package com.platform.notification.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.platform.events.EventTypes;
import com.platform.events.serde.EventObjectMapper;
import com.platform.notification.entity.Notification;
import com.platform.notification.entity.ProcessedEvent;
import com.platform.notification.repository.NotificationRepository;
import com.platform.notification.repository.ProcessedEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class OrderFinalEventsConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderFinalEventsConsumer.class);

    private final NotificationRepository notificationRepository;
    private final ProcessedEventRepository processedEventRepository;

    public OrderFinalEventsConsumer(NotificationRepository notificationRepository,
                                    ProcessedEventRepository processedEventRepository) {
        this.notificationRepository = notificationRepository;
        this.processedEventRepository = processedEventRepository;
    }

    @KafkaListener(topics = "order-events", groupId = "notification-service")
    @Transactional
    public void consume(String message) {
        try {
            JsonNode root = EventObjectMapper.instance().readTree(message);
            String eventType = root.get("eventType").asText();
            UUID eventId = UUID.fromString(root.get("eventId").asText());

            if (!EventTypes.ORDER_CONFIRMED.equals(eventType) && !EventTypes.ORDER_CANCELLED.equals(eventType)) {
                return;
            }

            if (processedEventRepository.existsById(eventId)) {
                log.info("Event {} already processed, skipping", eventId);
                return;
            }

            JsonNode payload = root.get("payload");
            UUID orderId = UUID.fromString(payload.get("orderId").asText());

            String notificationMessage;
            if (EventTypes.ORDER_CONFIRMED.equals(eventType)) {
                notificationMessage = String.format("Order %s has been confirmed. Thank you for your purchase!", orderId);
                log.info("[EMAIL] Sending confirmation email for order {}", orderId);
            } else {
                String reason = payload.has("reason") ? payload.get("reason").asText() : "Unknown";
                notificationMessage = String.format("Order %s has been cancelled. Reason: %s", orderId, reason);
                log.info("[EMAIL] Sending cancellation email for order {}: {}", orderId, reason);
            }

            Notification notification = new Notification(orderId, eventType, notificationMessage);
            notificationRepository.save(notification);
            processedEventRepository.save(new ProcessedEvent(eventId));

            log.info("Notification saved: orderId={}, type={}", orderId, eventType);
        } catch (Exception e) {
            log.error("Failed to process order final event: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to process notification event", e);
        }
    }
}
