package com.platform.events.order;

import java.util.UUID;

public record OrderConfirmedEvent(
        UUID orderId
) {}
