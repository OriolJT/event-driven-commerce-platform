package com.platform.order.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(name = "total_amount", nullable = false)
    private BigDecimal totalAmount;

    @Column(nullable = false)
    private String currency;

    @Column(name = "idempotency_key", unique = true)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    protected Order() {}

    public Order(UUID customerId, BigDecimal totalAmount, String currency, String idempotencyKey) {
        this.id = UUID.randomUUID();
        this.customerId = customerId;
        this.status = OrderStatus.PENDING;
        this.totalAmount = totalAmount;
        this.currency = currency;
        this.idempotencyKey = idempotencyKey;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void addItem(UUID productId, int quantity, BigDecimal unitPrice) {
        OrderItem item = new OrderItem(this, productId, quantity, unitPrice);
        items.add(item);
    }

    public boolean updateStatus(OrderStatus newStatus) {
        if (!this.status.canTransitionTo(newStatus)) {
            return false;
        }
        this.status = newStatus;
        this.updatedAt = Instant.now();
        return true;
    }

    public UUID getId() { return id; }
    public UUID getCustomerId() { return customerId; }
    public OrderStatus getStatus() { return status; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public String getCurrency() { return currency; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<OrderItem> getItems() { return items; }
}
