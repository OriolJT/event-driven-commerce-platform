package com.platform.events.inventory;

import java.util.UUID;

public record StockReleasedEvent(
        UUID orderId
) {}
