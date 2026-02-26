package com.platform.events.inventory;

import com.platform.events.OrderLineItem;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record StockReservedEvent(
        UUID orderId,
        List<OrderLineItem> items,
        BigDecimal totalAmount,
        String currency
) {}
