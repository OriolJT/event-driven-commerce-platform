package com.platform.payment.config;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConfig.class);

    @Bean
    public CommonErrorHandler kafkaErrorHandler(KafkaTemplate<?, ?> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (ConsumerRecord<?, ?> record, Exception ex) -> {
                    log.error("Sending record to DLT from topic {}: {}", record.topic(), ex.getMessage());
                    return new org.apache.kafka.common.TopicPartition(record.topic() + ".DLT", record.partition());
                });
        return new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3));
    }
}
