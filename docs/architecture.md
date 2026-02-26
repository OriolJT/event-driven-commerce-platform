# Architecture

## Service Boundaries

Each service owns its own database and communicates exclusively through Kafka events.

| Service | Port | Database | Publishes | Consumes |
|---------|:----:|----------|-----------|----------|
| **order-service** | 8081 | `orderdb` | `OrderCreated`, `OrderConfirmed`, `OrderCancelled`, `StockReleaseRequested` | `StockReserved`, `StockRejected`, `PaymentSucceeded`, `PaymentFailed` |
| **inventory-service** | 8083 | `inventorydb` | `StockReserved`, `StockRejected`, `StockReleased` | `OrderCreated`, `StockReleaseRequested` |
| **payment-service** | 8082 | `paymentdb` | `PaymentSucceeded`, `PaymentFailed` | `StockReserved` |
| **notification-service** | 8084 | `notificationdb` | — | `OrderConfirmed`, `OrderCancelled` |

---

## Topic Strategy

One topic per bounded context: `order-events`, `inventory-events`, `payment-events`.

- **Key** = `orderId` — preserves ordering per order across all partitions
- **Simpler operations** — fewer topics to manage than per-event-type topics
- **Consumer filtering** — consumers check the `eventType` field in the envelope

---

## Event Model

All events are wrapped in a generic `EventEnvelope<T>`:

```json
{
  "eventId": "uuid",
  "eventType": "OrderCreated",
  "occurredAt": "2025-01-15T10:30:00Z",
  "correlationId": "uuid (orderId)",
  "causationId": "uuid (causing eventId)",
  "version": 1,
  "payload": { ... }
}
```

| Field | Purpose |
|-------|---------|
| `correlationId` | Business correlation — always the `orderId` |
| `causationId` | The `eventId` of the event that caused this one |
| `eventId` | Used as Kafka message key for outbox idempotency |

---

## Outbox Pattern

Every service writes events to its own `outbox_events` table in the same DB transaction
as the business operation. A `@Scheduled` publisher polls unpublished rows every 500ms
and sends them to Kafka.

**Benefits:**

- Atomic consistency between state change and event publication
- Survives Kafka outages (events queued in DB)
- Simple implementation without CDC (Debezium)

**Trade-offs:**

- Slight latency (polling interval)
- Requires periodic cleanup of published rows

---

## Choreography vs Orchestration

This platform uses **saga choreography**: each service autonomously reacts to events.
There is no central orchestrator.

| | Choreography (this project) | Orchestration |
|-|----------------------------|---------------|
| **Coupling** | Loose — services only know events | Tight — orchestrator knows all steps |
| **Extensibility** | Easy to add new consumers | Requires orchestrator changes |
| **Failure mode** | No single point of failure | Orchestrator is a SPOF |
| **Visibility** | Harder to visualize full flow | Clear step-by-step visibility |
| **Debugging** | Requires distributed tracing | Centralized logs |

---

## Eventual Consistency

The system is eventually consistent. After `POST /api/orders`, the order status is
`PENDING`. It transitions asynchronously as each service processes events. Clients should
poll `GET /api/orders/{id}` to check final status.
