package com.platform.order.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.platform.events.EventTypes;
import com.platform.events.serde.EventObjectMapper;
import com.platform.order.entity.OrderStatus;
import com.platform.order.entity.ProcessedEvent;
import com.platform.order.repository.ProcessedEventRepository;
import com.platform.order.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class SagaEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(SagaEventConsumer.class);

    private final OrderService orderService;
    private final ProcessedEventRepository processedEventRepository;

    public SagaEventConsumer(OrderService orderService,
                             ProcessedEventRepository processedEventRepository) {
        this.orderService = orderService;
        this.processedEventRepository = processedEventRepository;
    }

    @KafkaListener(topics = "inventory-events", groupId = "order-service")
    @Transactional
    public void consumeInventoryEvents(String message) {
        processEvent(message);
    }

    @KafkaListener(topics = "payment-events", groupId = "order-service")
    @Transactional
    public void consumePaymentEvents(String message) {
        processEvent(message);
    }

    private void processEvent(String message) {
        try {
            JsonNode root = EventObjectMapper.instance().readTree(message);
            String eventType = root.get("eventType").asText();
            UUID eventId = UUID.fromString(root.get("eventId").asText());

            if (processedEventRepository.existsById(eventId)) {
                log.info("Event {} already processed, skipping", eventId);
                return;
            }

            JsonNode payload = root.get("payload");
            UUID orderId = UUID.fromString(payload.get("orderId").asText());

            switch (eventType) {
                case EventTypes.STOCK_RESERVED -> {
                    orderService.updateOrderStatus(orderId, OrderStatus.STOCK_RESERVED);
                    log.info("Order {} status updated to STOCK_RESERVED", orderId);
                }
                case EventTypes.STOCK_REJECTED -> {
                    String reason = payload.has("reason") ? payload.get("reason").asText() : "Stock unavailable";
                    orderService.cancelOrder(orderId, reason, false);
                    log.info("Order {} cancelled due to stock rejection: {}", orderId, reason);
                }
                case EventTypes.PAYMENT_SUCCEEDED -> {
                    orderService.confirmOrder(orderId);
                    log.info("Order {} confirmed after payment success", orderId);
                }
                case EventTypes.PAYMENT_FAILED -> {
                    String reason = payload.has("reason") ? payload.get("reason").asText() : "Payment failed";
                    orderService.cancelOrder(orderId, reason, true);
                    log.info("Order {} cancelled due to payment failure, stock release requested", orderId);
                }
                default -> log.debug("Ignoring event type: {}", eventType);
            }

            processedEventRepository.save(new ProcessedEvent(eventId));
        } catch (Exception e) {
            log.error("Failed to process saga event: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to process saga event", e);
        }
    }
}
