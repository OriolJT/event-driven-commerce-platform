package com.platform.order.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
public class OutboxCleanup {

    private static final Logger log = LoggerFactory.getLogger(OutboxCleanup.class);

    private final OutboxRepository outboxRepository;
    private final int retentionDays;

    public OutboxCleanup(OutboxRepository outboxRepository,
                         @Value("${outbox.cleanup.retention-days:7}") int retentionDays) {
        this.outboxRepository = outboxRepository;
        this.retentionDays = retentionDays;
    }

    @Scheduled(fixedDelayString = "${outbox.cleanup.interval-ms:3600000}")
    @Transactional
    public void cleanupPublishedEvents() {
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        int deleted = outboxRepository.deleteByPublishedTrueAndCreatedAtBefore(cutoff);
        if (deleted > 0) {
            log.info("Cleaned up {} published outbox events older than {} days", deleted, retentionDays);
        }
    }
}
