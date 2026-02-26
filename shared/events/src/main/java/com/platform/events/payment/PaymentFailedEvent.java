package com.platform.events.payment;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentFailedEvent(
        UUID orderId,
        BigDecimal amount,
        String reason
) {}
