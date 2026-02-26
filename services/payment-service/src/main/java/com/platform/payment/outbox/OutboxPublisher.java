package com.platform.payment.outbox;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private static final long MAX_BACKOFF_MS = 30_000;

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    private volatile int consecutiveFailures = 0;
    private volatile long nextAllowedRunMs = 0;

    public OutboxPublisher(OutboxRepository outboxRepository,
                           KafkaTemplate<String, String> kafkaTemplate,
                           MeterRegistry meterRegistry) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.meterRegistry = meterRegistry;
    }

    @Scheduled(fixedDelay = 500)
    public void publishPendingEvents() {
        if (System.currentTimeMillis() < nextAllowedRunMs) {
            return;
        }

        List<OutboxEvent> events = outboxRepository.findTop100ByPublishedFalseOrderByCreatedAtAsc();
        for (OutboxEvent event : events) {
            try {
                publishSingleEvent(event);
                consecutiveFailures = 0;
            } catch (Exception e) {
                consecutiveFailures++;
                long backoffMs = Math.min(500L * (1L << Math.min(consecutiveFailures, 6)), MAX_BACKOFF_MS);
                nextAllowedRunMs = System.currentTimeMillis() + backoffMs;
                meterRegistry.counter("outbox_publish_failures_total").increment();
                log.warn("Kafka send failed, backing off for {}ms (consecutive failures: {})",
                        backoffMs, consecutiveFailures);
                break;
            }
        }
    }

    @Transactional
    protected void publishSingleEvent(OutboxEvent event) {
        String topic = "payment-events";
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
