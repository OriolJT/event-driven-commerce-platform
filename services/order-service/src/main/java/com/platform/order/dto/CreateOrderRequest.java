package com.platform.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.List;
import java.util.UUID;

public record CreateOrderRequest(
        @NotNull UUID customerId,
        @NotEmpty @Valid List<OrderItemRequest> items,
        @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a 3-letter ISO code") String currency
) {
    public CreateOrderRequest {
        if (currency == null || currency.isBlank()) {
            currency = "EUR";
        }
    }
}
