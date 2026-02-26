package com.platform.order.entity;

import java.util.Set;

public enum OrderStatus {
    PENDING,
    STOCK_RESERVED,
    CONFIRMED,
    CANCELLED;

    public boolean canTransitionTo(OrderStatus target) {
        return allowedTransitions().contains(target);
    }

    private Set<OrderStatus> allowedTransitions() {
        return switch (this) {
            case PENDING -> Set.of(STOCK_RESERVED, CANCELLED);
            case STOCK_RESERVED -> Set.of(CONFIRMED, CANCELLED);
            case CONFIRMED, CANCELLED -> Set.of();
        };
    }
}
