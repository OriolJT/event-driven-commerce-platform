package com.platform.inventory;

import com.platform.events.EventEnvelope;
import com.platform.events.EventTypes;
import com.platform.events.OrderLineItem;
import com.platform.events.order.OrderCreatedEvent;
import com.platform.events.serde.EventObjectMapper;
import com.platform.inventory.outbox.OutboxEvent;
import com.platform.inventory.outbox.OutboxRepository;
import com.platform.inventory.repository.ProductRepository;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class InventoryServiceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private ProductRepository productRepository;

    @Test
    void shouldReserveStockOnOrderCreated() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID productId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        int stockBefore = productRepository.findById(productId).orElseThrow().getStock();

        OrderCreatedEvent event = new OrderCreatedEvent(
                orderId, UUID.randomUUID(),
                List.of(new OrderLineItem(productId, 2, new BigDecimal("29.99"))),
                new BigDecimal("59.98"), "EUR"
        );
        EventEnvelope<OrderCreatedEvent> envelope = EventEnvelope.wrap(
                EventTypes.ORDER_CREATED, event, orderId);

        String payload = EventObjectMapper.instance().writeValueAsString(envelope);
        kafkaTemplate.send(new ProducerRecord<>("order-events", orderId.toString(), payload));

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            List<OutboxEvent> events = outboxRepository.findByPublishedFalseOrderByCreatedAtAsc();
            assertThat(events).anyMatch(e -> e.getEventType().equals(EventTypes.STOCK_RESERVED)
                    && e.getAggregateId().equals(orderId));
        });

        int stockAfter = productRepository.findById(productId).orElseThrow().getStock();
        assertThat(stockAfter).isEqualTo(stockBefore - 2);
    }

    @Test
    void shouldRejectStockWhenInsufficientInventory() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID productId = UUID.fromString("44444444-4444-4444-4444-444444444444"); // 30 stock

        OrderCreatedEvent event = new OrderCreatedEvent(
                orderId, UUID.randomUUID(),
                List.of(new OrderLineItem(productId, 999, new BigDecimal("100.00"))),
                new BigDecimal("99900.00"), "EUR"
        );
        EventEnvelope<OrderCreatedEvent> envelope = EventEnvelope.wrap(
                EventTypes.ORDER_CREATED, event, orderId);

        String payload = EventObjectMapper.instance().writeValueAsString(envelope);
        kafkaTemplate.send(new ProducerRecord<>("order-events", orderId.toString(), payload));

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            List<OutboxEvent> events = outboxRepository.findByPublishedFalseOrderByCreatedAtAsc();
            assertThat(events).anyMatch(e -> e.getEventType().equals(EventTypes.STOCK_REJECTED)
                    && e.getAggregateId().equals(orderId));
        });
    }
}
