package com.platform.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record CreateOrderRequest(
        @NotNull UUID customerId,
        @NotEmpty @Valid List<OrderItemRequest> items,
        String currency
) {
    public CreateOrderRequest {
        if (currency == null || currency.isBlank()) {
            currency = "EUR";
        }
    }
}
