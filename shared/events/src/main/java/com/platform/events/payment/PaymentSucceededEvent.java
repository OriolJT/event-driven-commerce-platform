package com.platform.events.payment;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentSucceededEvent(
        UUID orderId,
        UUID paymentId,
        BigDecimal amount
) {}
