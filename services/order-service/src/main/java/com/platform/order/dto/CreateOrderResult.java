package com.platform.order.dto;

public record CreateOrderResult(
        OrderResponse response,
        boolean fromCache
) {}
