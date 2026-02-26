package com.platform.events.serde;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.platform.events.EventTypes;
import com.platform.events.inventory.StockRejectedEvent;
import com.platform.events.inventory.StockReleasedEvent;
import com.platform.events.inventory.StockReservedEvent;
import com.platform.events.order.OrderCancelledEvent;
import com.platform.events.order.OrderConfirmedEvent;
import com.platform.events.order.OrderCreatedEvent;
import com.platform.events.order.StockReleaseRequestedEvent;
import com.platform.events.payment.PaymentFailedEvent;
import com.platform.events.payment.PaymentSucceededEvent;

public final class EventObjectMapper {

    private static final ObjectMapper INSTANCE;

    static {
        INSTANCE = new ObjectMapper();
        INSTANCE.registerModule(new JavaTimeModule());

        INSTANCE.registerSubtypes(
                new NamedType(OrderCreatedEvent.class, EventTypes.ORDER_CREATED),
                new NamedType(OrderConfirmedEvent.class, EventTypes.ORDER_CONFIRMED),
                new NamedType(OrderCancelledEvent.class, EventTypes.ORDER_CANCELLED),
                new NamedType(StockReleaseRequestedEvent.class, EventTypes.STOCK_RELEASE_REQUESTED),
                new NamedType(StockReservedEvent.class, EventTypes.STOCK_RESERVED),
                new NamedType(StockRejectedEvent.class, EventTypes.STOCK_REJECTED),
                new NamedType(StockReleasedEvent.class, EventTypes.STOCK_RELEASED),
                new NamedType(PaymentSucceededEvent.class, EventTypes.PAYMENT_SUCCEEDED),
                new NamedType(PaymentFailedEvent.class, EventTypes.PAYMENT_FAILED)
        );
    }

    private EventObjectMapper() {}

    public static ObjectMapper instance() {
        return INSTANCE;
    }
}
