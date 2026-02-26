package com.platform.payment;

import com.platform.events.EventEnvelope;
import com.platform.events.EventTypes;
import com.platform.events.OrderLineItem;
import com.platform.events.inventory.StockReservedEvent;
import com.platform.events.serde.EventObjectMapper;
import com.platform.payment.outbox.OutboxEvent;
import com.platform.payment.outbox.OutboxRepository;
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
class PaymentServiceIntegrationTest {

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

    @Test
    void shouldProcessPaymentOnStockReserved() throws Exception {
        UUID orderId = UUID.randomUUID();

        StockReservedEvent event = new StockReservedEvent(
                orderId,
                List.of(new OrderLineItem(UUID.randomUUID(), 1, new BigDecimal("50.00"))),
                new BigDecimal("50.00"),
                "EUR"
        );
        EventEnvelope<StockReservedEvent> envelope = EventEnvelope.wrap(
                EventTypes.STOCK_RESERVED, event, orderId);

        String payload = EventObjectMapper.instance().writeValueAsString(envelope);
        kafkaTemplate.send(new ProducerRecord<>("inventory-events", orderId.toString(), payload));

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            List<OutboxEvent> events = outboxRepository.findByPublishedFalseOrderByCreatedAtAsc();
            assertThat(events).anyMatch(e ->
                    (e.getEventType().equals(EventTypes.PAYMENT_SUCCEEDED) ||
                     e.getEventType().equals(EventTypes.PAYMENT_FAILED))
                    && e.getAggregateId().equals(orderId));
        });
    }
}
