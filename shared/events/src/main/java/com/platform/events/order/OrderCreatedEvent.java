package com.platform.events.order;

import com.platform.events.OrderLineItem;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record OrderCreatedEvent(
        UUID orderId,
        UUID customerId,
        List<OrderLineItem> items,
        BigDecimal totalAmount,
        String currency
) {}
