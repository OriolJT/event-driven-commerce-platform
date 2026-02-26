package com.platform.order.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemRequest(
        @NotNull UUID productId,
        @Min(1) int quantity,
        @NotNull @DecimalMin("0.01") BigDecimal unitPrice
) {}
