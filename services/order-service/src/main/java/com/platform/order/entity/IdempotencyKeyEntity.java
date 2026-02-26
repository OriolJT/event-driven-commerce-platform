package com.platform.order.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "idempotency_keys")
public class IdempotencyKeyEntity {

    @Id
    @Column(name = "key")
    private String key;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "request_hash", nullable = false)
    private String requestHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_body", columnDefinition = "jsonb", nullable = false)
    private String responseBody;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected IdempotencyKeyEntity() {}

    public IdempotencyKeyEntity(String key, UUID orderId, String requestHash, String responseBody) {
        this.key = key;
        this.orderId = orderId;
        this.requestHash = requestHash;
        this.responseBody = responseBody;
        this.createdAt = Instant.now();
    }

    public String getKey() { return key; }
    public UUID getOrderId() { return orderId; }
    public String getRequestHash() { return requestHash; }
    public String getResponseBody() { return responseBody; }
    public Instant getCreatedAt() { return createdAt; }
}
