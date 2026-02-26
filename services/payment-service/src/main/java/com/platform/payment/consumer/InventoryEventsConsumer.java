package com.platform.payment.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.platform.events.EventTypes;
import com.platform.events.serde.EventObjectMapper;
import com.platform.payment.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Component
public class InventoryEventsConsumer {

    private static final Logger log = LoggerFactory.getLogger(InventoryEventsConsumer.class);

    private final PaymentService paymentService;

    public InventoryEventsConsumer(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @KafkaListener(topics = "inventory-events", groupId = "payment-service")
    public void consume(String message) {
        try {
            JsonNode root = EventObjectMapper.instance().readTree(message);
            String eventType = root.get("eventType").asText();
            UUID eventId = UUID.fromString(root.get("eventId").asText());

            if (EventTypes.STOCK_RESERVED.equals(eventType)) {
                JsonNode payload = root.get("payload");
                UUID orderId = UUID.fromString(payload.get("orderId").asText());
                BigDecimal totalAmount = new BigDecimal(payload.get("totalAmount").asText());
                paymentService.processPayment(eventId, orderId, totalAmount);
            } else {
                log.debug("Ignoring event type: {}", eventType);
            }
        } catch (Exception e) {
            log.error("Failed to process inventory event: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to process event", e);
        }
    }
}
