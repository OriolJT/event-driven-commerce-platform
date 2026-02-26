package com.platform.events.serde;

import com.platform.events.EventEnvelope;
import org.apache.kafka.common.serialization.Serializer;

public class EventEnvelopeSerializer implements Serializer<EventEnvelope<?>> {

    @Override
    public byte[] serialize(String topic, EventEnvelope<?> data) {
        if (data == null) return null;
        try {
            return EventObjectMapper.instance().writeValueAsBytes(data);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize EventEnvelope", e);
        }
    }
}
