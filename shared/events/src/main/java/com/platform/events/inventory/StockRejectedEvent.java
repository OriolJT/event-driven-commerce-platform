package com.platform.events.inventory;

import java.util.UUID;

public record StockRejectedEvent(
        UUID orderId,
        String reason
) {}
