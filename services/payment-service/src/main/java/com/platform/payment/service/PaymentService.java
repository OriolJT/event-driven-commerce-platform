package com.platform.payment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.platform.events.EventEnvelope;
import com.platform.events.EventTypes;
import com.platform.events.payment.PaymentFailedEvent;
import com.platform.events.payment.PaymentSucceededEvent;
import com.platform.events.serde.EventObjectMapper;
import com.platform.payment.entity.Payment;
import com.platform.payment.entity.ProcessedEvent;
import com.platform.payment.outbox.OutboxEvent;
import com.platform.payment.outbox.OutboxRepository;
import com.platform.payment.repository.PaymentRepository;
import com.platform.payment.repository.ProcessedEventRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final OutboxRepository outboxRepository;
    private final MeterRegistry meterRegistry;
    private final double successRate;

    public PaymentService(PaymentRepository paymentRepository,
                          ProcessedEventRepository processedEventRepository,
                          OutboxRepository outboxRepository,
                          MeterRegistry meterRegistry,
                          @Value("${payment.simulate.success-rate:0.8}") double successRate) {
        this.paymentRepository = paymentRepository;
        this.processedEventRepository = processedEventRepository;
        this.outboxRepository = outboxRepository;
        this.meterRegistry = meterRegistry;
        this.successRate = successRate;
    }

    @Transactional
    public void processPayment(UUID eventId, UUID orderId, BigDecimal amount) {
        if (processedEventRepository.existsById(eventId)) {
            log.info("Event {} already processed, skipping", eventId);
            return;
        }

        boolean success = simulatePayment(orderId);

        if (success) {
            Payment payment = new Payment(orderId, amount, "SUCCEEDED");
            paymentRepository.save(payment);

            PaymentSucceededEvent event = new PaymentSucceededEvent(orderId, payment.getId(), amount);
            EventEnvelope<PaymentSucceededEvent> envelope = EventEnvelope.wrap(
                    EventTypes.PAYMENT_SUCCEEDED, event, orderId);
            saveOutboxEvent("Payment", orderId, EventTypes.PAYMENT_SUCCEEDED, envelope);

            meterRegistry.counter("payments_processed_total", "outcome", "success").increment();
            log.info("Payment succeeded for order {}: paymentId={}", orderId, payment.getId());
        } else {
            Payment payment = new Payment(orderId, amount, "FAILED");
            paymentRepository.save(payment);

            PaymentFailedEvent event = new PaymentFailedEvent(orderId, amount, "Payment declined by provider");
            EventEnvelope<PaymentFailedEvent> envelope = EventEnvelope.wrap(
                    EventTypes.PAYMENT_FAILED, event, orderId);
            saveOutboxEvent("Payment", orderId, EventTypes.PAYMENT_FAILED, envelope);

            meterRegistry.counter("payments_processed_total", "outcome", "failure").increment();
            log.warn("Payment failed for order {}", orderId);
        }

        processedEventRepository.save(new ProcessedEvent(eventId));
    }

    private boolean simulatePayment(UUID orderId) {
        // Deterministic based on orderId hash for reproducibility
        int hash = Math.abs(orderId.hashCode());
        return (hash % 100) < (successRate * 100);
    }

    private void saveOutboxEvent(String aggregateType, UUID aggregateId, String eventType, Object envelope) {
        try {
            String payload = EventObjectMapper.instance().writeValueAsString(envelope);
            outboxRepository.save(new OutboxEvent(aggregateType, aggregateId, eventType, payload));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize event for outbox", e);
        }
    }
}
