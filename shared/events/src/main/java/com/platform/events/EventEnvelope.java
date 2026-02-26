package com.platform.events;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.time.Instant;
import java.util.UUID;

public record EventEnvelope<T>(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        UUID correlationId,
        UUID causationId,
        int version,
        @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "eventType", include = JsonTypeInfo.As.EXTERNAL_PROPERTY)
        T payload
) {
    public static <T> EventEnvelope<T> wrap(String eventType, T payload, UUID correlationId, UUID causationId) {
        return new EventEnvelope<>(
                UUID.randomUUID(),
                eventType,
                Instant.now(),
                correlationId,
                causationId,
                1,
                payload
        );
    }

    public static <T> EventEnvelope<T> wrap(String eventType, T payload, UUID correlationId) {
        return wrap(eventType, payload, correlationId, correlationId);
    }
}
