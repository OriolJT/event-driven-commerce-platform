package com.platform.order.outbox;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private static final Map<String, String> AGGREGATE_TO_TOPIC = Map.of(
            "Order", "order-events"
    );

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    public OutboxPublisher(OutboxRepository outboxRepository,
                           KafkaTemplate<String, String> kafkaTemplate,
                           MeterRegistry meterRegistry) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.meterRegistry = meterRegistry;
    }

    @Scheduled(fixedDelay = 500)
    public void publishPendingEvents() {
        List<OutboxEvent> events = outboxRepository.findTop100ByPublishedFalseOrderByCreatedAtAsc();
        for (OutboxEvent event : events) {
            try {
                publishSingleEvent(event);
            } catch (Exception e) {
                log.error("Failed to publish outbox event {}: {}", event.getId(), e.getMessage());
                break;
            }
        }
    }

    @Transactional
    protected void publishSingleEvent(OutboxEvent event) {
        String topic = AGGREGATE_TO_TOPIC.getOrDefault(event.getAggregateType(), "order-events");
        try {
            kafkaTemplate.send(topic, event.getAggregateId().toString(), event.getPayload()).get();
            event.markPublished();
            outboxRepository.save(event);
            meterRegistry.counter("outbox_published_total").increment();
            log.info("Published outbox event {} of type {} to topic {}",
                    event.getId(), event.getEventType(), topic);
        } catch (Exception e) {
            throw new RuntimeException("Failed to publish event " + event.getId(), e);
        }
    }
}
