package com.platform.events.order;

import java.util.UUID;

public record OrderCancelledEvent(
        UUID orderId,
        String reason
) {}
