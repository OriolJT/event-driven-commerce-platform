package com.platform.order.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.platform.events.EventEnvelope;
import com.platform.events.EventTypes;
import com.platform.events.OrderLineItem;
import com.platform.events.order.OrderCancelledEvent;
import com.platform.events.order.OrderConfirmedEvent;
import com.platform.events.order.OrderCreatedEvent;
import com.platform.events.order.StockReleaseRequestedEvent;
import com.platform.events.serde.EventObjectMapper;
import com.platform.order.dto.CreateOrderRequest;
import com.platform.order.dto.CreateOrderResult;
import com.platform.order.dto.OrderResponse;
import com.platform.order.entity.IdempotencyKeyEntity;
import com.platform.order.entity.Order;
import com.platform.order.entity.OrderStatus;
import com.platform.order.outbox.OutboxEvent;
import com.platform.order.outbox.OutboxRepository;
import com.platform.order.repository.IdempotencyRepository;
import com.platform.order.repository.OrderRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final OutboxRepository outboxRepository;
    private final IdempotencyRepository idempotencyRepository;
    private final MeterRegistry meterRegistry;

    public OrderService(OrderRepository orderRepository,
                        OutboxRepository outboxRepository,
                        IdempotencyRepository idempotencyRepository,
                        MeterRegistry meterRegistry) {
        this.orderRepository = orderRepository;
        this.outboxRepository = outboxRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public CreateOrderResult createOrder(CreateOrderRequest request, String idempotencyKey) {
        String requestHash = hashRequest(request);

        var existing = idempotencyRepository.findById(idempotencyKey);
        if (existing.isPresent()) {
            IdempotencyKeyEntity entity = existing.get();
            if (entity.getRequestHash().equals(requestHash)) {
                try {
                    OrderResponse cached = EventObjectMapper.instance().readValue(
                            entity.getResponseBody(), OrderResponse.class);
                    return new CreateOrderResult(cached, true);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException("Failed to deserialize cached response", e);
                }
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Idempotency key already used with different request payload");
        }

        BigDecimal totalAmount = request.items().stream()
                .map(item -> item.unitPrice().multiply(BigDecimal.valueOf(item.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Order order = new Order(request.customerId(), totalAmount, request.currency(), idempotencyKey);
        request.items().forEach(item ->
                order.addItem(item.productId(), item.quantity(), item.unitPrice()));

        orderRepository.save(order);

        List<OrderLineItem> lineItems = request.items().stream()
                .map(item -> new OrderLineItem(item.productId(), item.quantity(), item.unitPrice()))
                .toList();

        OrderCreatedEvent event = new OrderCreatedEvent(
                order.getId(), order.getCustomerId(), lineItems, totalAmount, order.getCurrency());
        EventEnvelope<OrderCreatedEvent> envelope = EventEnvelope.wrap(
                EventTypes.ORDER_CREATED, event, order.getId());

        saveOutboxEvent("Order", order.getId(), EventTypes.ORDER_CREATED, envelope);

        OrderResponse response = OrderResponse.from(order);

        try {
            String responseJson = EventObjectMapper.instance().writeValueAsString(response);
            idempotencyRepository.save(new IdempotencyKeyEntity(
                    idempotencyKey, order.getId(), requestHash, responseJson));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize response for idempotency cache", e);
        }

        meterRegistry.counter("orders_created_total").increment();
        log.info("Order created: id={}, status={}", order.getId(), order.getStatus());
        return new CreateOrderResult(response, false);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        return OrderResponse.from(order);
    }

    @Transactional
    public void updateOrderStatus(UUID orderId, OrderStatus newStatus) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        OrderStatus oldStatus = order.getStatus();
        if (!order.updateStatus(newStatus)) {
            log.warn("Order {} ignoring invalid transition: {} -> {}", orderId, oldStatus, newStatus);
            return;
        }
        orderRepository.save(order);
        log.info("Order {} status changed: {} -> {}", orderId, oldStatus, newStatus);
    }

    @Transactional
    public void confirmOrder(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        if (!order.updateStatus(OrderStatus.CONFIRMED)) {
            log.warn("Order {} cannot be confirmed from status {}", orderId, order.getStatus());
            return;
        }
        orderRepository.save(order);

        OrderConfirmedEvent event = new OrderConfirmedEvent(orderId);
        EventEnvelope<OrderConfirmedEvent> envelope = EventEnvelope.wrap(
                EventTypes.ORDER_CONFIRMED, event, orderId);
        saveOutboxEvent("Order", orderId, EventTypes.ORDER_CONFIRMED, envelope);

        meterRegistry.counter("orders_confirmed_total").increment();
        log.info("Order {} confirmed", orderId);
    }

    @Transactional
    public void cancelOrder(UUID orderId, String reason, boolean releaseStock) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        if (!order.updateStatus(OrderStatus.CANCELLED)) {
            log.warn("Order {} cannot be cancelled from status {}", orderId, order.getStatus());
            return;
        }
        orderRepository.save(order);

        OrderCancelledEvent cancelledEvent = new OrderCancelledEvent(orderId, reason);
        EventEnvelope<OrderCancelledEvent> envelope = EventEnvelope.wrap(
                EventTypes.ORDER_CANCELLED, cancelledEvent, orderId);
        saveOutboxEvent("Order", orderId, EventTypes.ORDER_CANCELLED, envelope);

        if (releaseStock) {
            List<OrderLineItem> lineItems = order.getItems().stream()
                    .map(item -> new OrderLineItem(item.getProductId(), item.getQuantity(), item.getUnitPrice()))
                    .toList();
            StockReleaseRequestedEvent releaseEvent = new StockReleaseRequestedEvent(orderId, lineItems);
            EventEnvelope<StockReleaseRequestedEvent> releaseEnvelope = EventEnvelope.wrap(
                    EventTypes.STOCK_RELEASE_REQUESTED, releaseEvent, orderId);
            saveOutboxEvent("Order", orderId, EventTypes.STOCK_RELEASE_REQUESTED, releaseEnvelope);
        }

        meterRegistry.counter("orders_cancelled_total").increment();
        log.info("Order {} cancelled: {}", orderId, reason);
    }

    private void saveOutboxEvent(String aggregateType, UUID aggregateId, String eventType, Object envelope) {
        try {
            String payload = EventObjectMapper.instance().writeValueAsString(envelope);
            outboxRepository.save(new OutboxEvent(aggregateType, aggregateId, eventType, payload));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize event for outbox", e);
        }
    }

    private String hashRequest(CreateOrderRequest request) {
        try {
            String json = EventObjectMapper.instance().writeValueAsString(request);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(json.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to hash request", e);
        }
    }
}
