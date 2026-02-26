package com.platform.order.dto;

import com.platform.order.entity.Order;
import com.platform.order.entity.OrderItem;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        UUID customerId,
        String status,
        BigDecimal totalAmount,
        String currency,
        List<ItemResponse> items,
        Instant createdAt,
        Instant updatedAt
) {
    public static OrderResponse from(Order order) {
        List<ItemResponse> items = order.getItems().stream()
                .map(ItemResponse::from)
                .toList();
        return new OrderResponse(
                order.getId(),
                order.getCustomerId(),
                order.getStatus().name(),
                order.getTotalAmount(),
                order.getCurrency(),
                items,
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }

    public record ItemResponse(UUID productId, int quantity, BigDecimal unitPrice) {
        public static ItemResponse from(OrderItem item) {
            return new ItemResponse(item.getProductId(), item.getQuantity(), item.getUnitPrice());
        }
    }
}
