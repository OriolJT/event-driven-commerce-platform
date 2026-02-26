package com.platform.events.order;

import com.platform.events.OrderLineItem;

import java.util.List;
import java.util.UUID;

public record StockReleaseRequestedEvent(
        UUID orderId,
        List<OrderLineItem> items
) {}
