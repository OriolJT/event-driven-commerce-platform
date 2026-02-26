package com.platform.events.serde;

import com.platform.events.EventEnvelope;
import org.apache.kafka.common.serialization.Deserializer;

public class EventEnvelopeDeserializer implements Deserializer<EventEnvelope<?>> {

    @Override
    public EventEnvelope<?> deserialize(String topic, byte[] data) {
        if (data == null) return null;
        try {
            return EventObjectMapper.instance().readValue(data,
                    EventObjectMapper.instance().getTypeFactory()
                            .constructParametricType(EventEnvelope.class, Object.class));
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize EventEnvelope", e);
        }
    }
}
