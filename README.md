# Event-Driven Commerce Platform

A production-style event-driven microservices platform in Java demonstrating Kafka saga choreography, outbox pattern, idempotency, resilience, and observability.

## Architecture

```
                        ┌──────────────┐
  POST /api/orders ───→ │ order-service │──→ order-events
                        └──────┬───────┘         │
                               ↑                 ↓
                    inventory-events    ┌──────────────────┐
                    payment-events      │ inventory-service │──→ inventory-events
                               ↑        └──────────────────┘         │
                               │                                     ↓
                        ┌──────┴────────┐               ┌──────────────────┐
                        │ order-service  │←──────────────│ payment-service  │
                        └───────────────┘                └──────────────────┘
                               │                                    ↑
                        order-events                      payment-events
                               │
                        ┌──────────────────────┐
                        │ notification-service  │
                        └──────────────────────┘
```

## Prerequisites

- Java 21 (Temurin)
- Maven 3.9+
- Docker + Docker Compose

## Quick Start

```bash
# Build all modules
mvn clean install -DskipTests

# Build Docker images
mvn -pl services/order-service,services/payment-service,services/inventory-service,services/notification-service jib:dockerBuild

# Start infrastructure + services
cd infra && docker compose up -d

# Wait for services to be healthy, then create an order
curl -X POST http://localhost:8081/api/orders \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: test-1" \
  -d '{
    "customerId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
    "items": [
      {
        "productId": "11111111-1111-1111-1111-111111111111",
        "quantity": 2,
        "unitPrice": 29.99
      }
    ],
    "currency": "EUR"
  }'

# Check order status (will transition through PENDING → STOCK_RESERVED → CONFIRMED/CANCELLED)
curl http://localhost:8081/api/orders/{orderId}
```

## Service Ports

| Service | Port | Swagger UI |
|---------|------|------------|
| order-service | 8081 | http://localhost:8081/swagger-ui.html |
| payment-service | 8082 | http://localhost:8082/swagger-ui.html |
| inventory-service | 8083 | http://localhost:8083/swagger-ui.html |
| notification-service | 8084 | http://localhost:8084/swagger-ui.html |

## Observability

| Tool | URL | Credentials |
|------|-----|-------------|
| Prometheus | http://localhost:9090 | - |
| Grafana | http://localhost:3000 | admin/admin |
| Tempo (traces) | Grafana → Explore → Tempo | - |

## Running Tests

```bash
# All tests (requires Docker for Testcontainers)
mvn test

# Specific service
mvn test -pl services/order-service -am
```

## Saga Flow

1. `POST /api/orders` → order created (PENDING) → `OrderCreated` event
2. inventory-service reserves stock → `StockReserved` event (or `StockRejected`)
3. payment-service processes payment → `PaymentSucceeded` event (or `PaymentFailed`)
4. order-service updates final status → `OrderConfirmed`/`OrderCancelled` event
5. notification-service sends email notification

## Key Patterns

- **Outbox Pattern**: All services write events to an outbox table in the same DB transaction, then a scheduled publisher sends to Kafka
- **Idempotency**: `Idempotency-Key` header on order creation; `processed_events` table in each consumer
- **Saga Choreography**: Services react to events autonomously, no central orchestrator
- **Compensation**: Payment failure triggers stock release via `StockReleaseRequested` event
- **Dead Letter Topics**: Failed messages routed to `<topic>.DLT` after 3 retries

## Available Products (Seeded)

| Product ID | Name | Stock |
|-----------|------|-------|
| 11111111-1111-1111-1111-111111111111 | Wireless Mouse | 100 |
| 22222222-2222-2222-2222-222222222222 | Mechanical Keyboard | 50 |
| 33333333-3333-3333-3333-333333333333 | USB-C Hub | 75 |
| 44444444-4444-4444-4444-444444444444 | 27" Monitor | 30 |
| 55555555-5555-5555-5555-555555555555 | Laptop Stand | 200 |
