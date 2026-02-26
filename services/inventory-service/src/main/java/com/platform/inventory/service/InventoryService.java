package com.platform.inventory.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.platform.events.EventEnvelope;
import com.platform.events.EventTypes;
import com.platform.events.OrderLineItem;
import com.platform.events.inventory.StockRejectedEvent;
import com.platform.events.inventory.StockReleasedEvent;
import com.platform.events.inventory.StockReservedEvent;
import com.platform.events.serde.EventObjectMapper;
import com.platform.inventory.entity.ProcessedEvent;
import com.platform.inventory.entity.Product;
import com.platform.inventory.entity.Reservation;
import com.platform.inventory.outbox.OutboxEvent;
import com.platform.inventory.outbox.OutboxRepository;
import com.platform.inventory.repository.ProcessedEventRepository;
import com.platform.inventory.repository.ProductRepository;
import com.platform.inventory.repository.ReservationRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    private final ProductRepository productRepository;
    private final ReservationRepository reservationRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final OutboxRepository outboxRepository;
    private final MeterRegistry meterRegistry;

    public InventoryService(ProductRepository productRepository,
                            ReservationRepository reservationRepository,
                            ProcessedEventRepository processedEventRepository,
                            OutboxRepository outboxRepository,
                            MeterRegistry meterRegistry) {
        this.productRepository = productRepository;
        this.reservationRepository = reservationRepository;
        this.processedEventRepository = processedEventRepository;
        this.outboxRepository = outboxRepository;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public void handleOrderCreated(UUID eventId, UUID orderId, List<OrderLineItem> items,
                                   java.math.BigDecimal totalAmount, String currency) {
        if (processedEventRepository.existsById(eventId)) {
            log.info("Event {} already processed, skipping", eventId);
            return;
        }

        List<Reservation> reservations = new ArrayList<>();
        boolean allReserved = true;
        String failureReason = null;

        for (OrderLineItem item : items) {
            Product product = productRepository.findById(item.productId()).orElse(null);
            if (product == null) {
                allReserved = false;
                failureReason = "Product not found: " + item.productId();
                break;
            }
            if (!product.reserveStock(item.quantity())) {
                allReserved = false;
                failureReason = "Insufficient stock for product: " + product.getName();
                break;
            }
            productRepository.save(product);
            reservations.add(new Reservation(orderId, item.productId(), item.quantity()));
        }

        if (allReserved) {
            reservationRepository.saveAll(reservations);

            StockReservedEvent event = new StockReservedEvent(orderId, items, totalAmount, currency);
            EventEnvelope<StockReservedEvent> envelope = EventEnvelope.wrap(
                    EventTypes.STOCK_RESERVED, event, orderId);
            saveOutboxEvent("Inventory", orderId, EventTypes.STOCK_RESERVED, envelope);
            meterRegistry.counter("stock_reserved_total").increment();
            log.info("Stock reserved for order {}", orderId);
        } else {
            // Rollback any reservations made so far
            for (Reservation res : reservations) {
                Product product = productRepository.findById(res.getProductId()).orElseThrow();
                product.releaseStock(res.getQuantity());
                productRepository.save(product);
            }

            StockRejectedEvent event = new StockRejectedEvent(orderId, failureReason);
            EventEnvelope<StockRejectedEvent> envelope = EventEnvelope.wrap(
                    EventTypes.STOCK_REJECTED, event, orderId);
            saveOutboxEvent("Inventory", orderId, EventTypes.STOCK_REJECTED, envelope);
            log.warn("Stock rejected for order {}: {}", orderId, failureReason);
        }

        processedEventRepository.save(new ProcessedEvent(eventId));
    }

    @Transactional
    public void handleStockReleaseRequested(UUID eventId, UUID orderId) {
        if (processedEventRepository.existsById(eventId)) {
            log.info("Event {} already processed, skipping", eventId);
            return;
        }

        List<Reservation> reservations = reservationRepository.findByOrderIdAndStatus(orderId, "RESERVED");
        for (Reservation reservation : reservations) {
            Product product = productRepository.findById(reservation.getProductId()).orElseThrow();
            product.releaseStock(reservation.getQuantity());
            productRepository.save(product);
            reservation.release();
            reservationRepository.save(reservation);
        }

        StockReleasedEvent event = new StockReleasedEvent(orderId);
        EventEnvelope<StockReleasedEvent> envelope = EventEnvelope.wrap(
                EventTypes.STOCK_RELEASED, event, orderId);
        saveOutboxEvent("Inventory", orderId, EventTypes.STOCK_RELEASED, envelope);

        processedEventRepository.save(new ProcessedEvent(eventId));
        log.info("Stock released for order {}", orderId);
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
