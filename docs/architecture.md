# Architecture

## Service Boundaries

Each service owns its own database and communicates exclusively through Kafka events.

### Order Service (port 8081)
- Accepts order creation via REST API
- Manages order lifecycle (PENDING → STOCK_RESERVED → CONFIRMED/CANCELLED)
- Publishes: `OrderCreated`, `OrderConfirmed`, `OrderCancelled`, `StockReleaseRequested`
- Consumes: `StockReserved`, `StockRejected`, `PaymentSucceeded`, `PaymentFailed`

### Inventory Service (port 8083)
- Manages product stock and reservations
- Uses pessimistic locking for stock operations
- Publishes: `StockReserved`, `StockRejected`, `StockReleased`
- Consumes: `OrderCreated`, `StockReleaseRequested`

### Payment Service (port 8082)
- Simulates payment processing (configurable success rate)
- Publishes: `PaymentSucceeded`, `PaymentFailed`
- Consumes: `StockReserved`

### Notification Service (port 8084)
- Logs mock email notifications for final order states
- Consumes: `OrderConfirmed`, `OrderCancelled`

## Topic Strategy

One topic per bounded context: `order-events`, `inventory-events`, `payment-events`.
- Key = orderId (preserves ordering per order)
- Simpler operations than per-event-type topics
- Consumer filtering by `eventType` field in envelope

## Event Model

All events wrapped in `EventEnvelope<T>`:
```
eventId, eventType, occurredAt, correlationId, causationId, version, payload
```

- `correlationId` = orderId (business correlation)
- `causationId` = eventId of the causing event
- `eventId` used as Kafka message key for outbox idempotency

## Outbox Pattern

Every service writes events to its own `outbox_events` table in the same DB transaction as the business operation. A `@Scheduled` publisher polls unpublished rows every 500ms and sends them to Kafka.

Benefits:
- Atomic consistency between state change and event publication
- Survives Kafka outages (events queued in DB)
- Simple implementation without CDC (Debezium)

Trade-offs:
- Slight latency (polling interval)
- Requires cleanup of published rows (not implemented yet)

## Choreography vs Orchestration

This platform uses **saga choreography**: each service autonomously reacts to events. There is no central orchestrator.

Pros:
- Services are loosely coupled
- Easy to add new consumers
- No single point of failure

Cons:
- Harder to visualize the full saga flow
- Debugging requires distributed tracing
- Compensation logic spread across services

## Eventual Consistency

The system is eventually consistent. After `POST /api/orders`, the order status is `PENDING`. It transitions asynchronously as each service processes events. Clients should poll `GET /api/orders/{id}` to check final status.
