package com.platform.inventory.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

    List<OutboxEvent> findByPublishedFalseOrderByCreatedAtAsc();

    List<OutboxEvent> findTop100ByPublishedFalseOrderByCreatedAtAsc();

    @Modifying
    int deleteByPublishedTrueAndCreatedAtBefore(Instant cutoff);
}
