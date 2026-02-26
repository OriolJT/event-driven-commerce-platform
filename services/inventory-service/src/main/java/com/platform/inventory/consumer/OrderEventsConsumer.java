package com.platform.inventory.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.platform.events.EventTypes;
import com.platform.events.OrderLineItem;
import com.platform.events.serde.EventObjectMapper;
import com.platform.inventory.service.InventoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class OrderEventsConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderEventsConsumer.class);

    private final InventoryService inventoryService;

    public OrderEventsConsumer(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @KafkaListener(topics = "order-events", groupId = "inventory-service")
    public void consume(String message) {
        try {
            JsonNode root = EventObjectMapper.instance().readTree(message);
            String eventType = root.get("eventType").asText();
            UUID eventId = UUID.fromString(root.get("eventId").asText());

            switch (eventType) {
                case EventTypes.ORDER_CREATED -> handleOrderCreated(root, eventId);
                case EventTypes.STOCK_RELEASE_REQUESTED -> handleStockReleaseRequested(root, eventId);
                default -> log.debug("Ignoring event type: {}", eventType);
            }
        } catch (Exception e) {
            log.error("Failed to process order event: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to process event", e);
        }
    }

    private void handleOrderCreated(JsonNode root, UUID eventId) throws Exception {
        JsonNode payload = root.get("payload");
        UUID orderId = UUID.fromString(payload.get("orderId").asText());
        BigDecimal totalAmount = new BigDecimal(payload.get("totalAmount").asText());
        String currency = payload.get("currency").asText();

        List<OrderLineItem> items = new ArrayList<>();
        for (JsonNode itemNode : payload.get("items")) {
            items.add(new OrderLineItem(
                    UUID.fromString(itemNode.get("productId").asText()),
                    itemNode.get("quantity").asInt(),
                    new BigDecimal(itemNode.get("unitPrice").asText())
            ));
        }

        inventoryService.handleOrderCreated(eventId, orderId, items, totalAmount, currency);
    }

    private void handleStockReleaseRequested(JsonNode root, UUID eventId) {
        JsonNode payload = root.get("payload");
        UUID orderId = UUID.fromString(payload.get("orderId").asText());
        inventoryService.handleStockReleaseRequested(eventId, orderId);
    }
}
