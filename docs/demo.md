# Demo Walkthrough

Step-by-step guide to run the platform and observe a complete saga execution.

## Prerequisites

- Java 21
- Maven 3.9+
- Docker Desktop running

## 1. Build and Start

```bash
# Build all modules
mvn clean install -DskipTests

# Build Docker images
mvn -pl services/order-service,services/payment-service,services/inventory-service,services/notification-service \
    jib:dockerBuild

# Start the full stack
cd infra && docker compose up -d
```

Wait for all containers to be healthy (~30 seconds):

```bash
docker compose ps
```

Expected output — all services should show `running`:

```
NAME                          STATUS
platform-postgres             running (healthy)
platform-kafka                running (healthy)
platform-order-service        running
platform-payment-service      running
platform-inventory-service    running
platform-notification-service running
platform-prometheus           running
platform-grafana              running
platform-tempo                running
```

## 2. Create an Order

```bash
curl -s -X POST http://localhost:8081/api/orders \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: demo-001" \
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
  }' | jq .
```

Expected response (HTTP 201):

```json
{
  "id": "d3f1a2b4-...",
  "customerId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
  "status": "PENDING",
  "totalAmount": 59.98,
  "currency": "EUR",
  "createdAt": "2025-01-15T10:30:00Z"
}
```

Save the order ID:

```bash
ORDER_ID="<paste the id from above>"
```

## 3. Watch the Saga Complete

Poll the order status — it should transition within a few seconds:

```bash
# Poll until final state
for i in 1 2 3 4 5; do
  echo "--- Attempt $i ---"
  curl -s http://localhost:8081/api/orders/$ORDER_ID | jq '.status'
  sleep 2
done
```

Expected status progression:

```
"PENDING"          → initial state
"STOCK_RESERVED"   → inventory reserved stock
"CONFIRMED"        → payment succeeded (or "CANCELLED" if payment failed)
```

## 4. Test Idempotency

Repeat the exact same request:

```bash
curl -s -X POST http://localhost:8081/api/orders \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: demo-001" \
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
  }' | jq .
```

Expected: **HTTP 200** with the same response body (cache hit, no duplicate order created).

Now try the same key with a different payload:

```bash
curl -s -o /dev/null -w "%{http_code}" -X POST http://localhost:8081/api/orders \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: demo-001" \
  -d '{
    "customerId": "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
    "items": [
      {
        "productId": "22222222-2222-2222-2222-222222222222",
        "quantity": 1,
        "unitPrice": 49.99
      }
    ],
    "currency": "EUR"
  }'
```

Expected: **HTTP 409** (Conflict — same key, different payload).

## 5. Test Stock Rejection

Order a quantity larger than available stock (Wireless Mouse has 100 units):

```bash
curl -s -X POST http://localhost:8081/api/orders \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: demo-002" \
  -d '{
    "customerId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
    "items": [
      {
        "productId": "11111111-1111-1111-1111-111111111111",
        "quantity": 9999,
        "unitPrice": 29.99
      }
    ],
    "currency": "EUR"
  }' | jq .
```

After a few seconds, check the status:

```bash
curl -s http://localhost:8081/api/orders/<order-id> | jq '.status'
```

Expected: `"CANCELLED"` — inventory rejected the stock reservation.

## 6. View Observability

### Grafana Dashboard

Open [http://localhost:3000](http://localhost:3000) (login: `admin` / `admin`).

Navigate to **Dashboards > Platform Service Overview** to see:
- Request rates and latency percentiles
- Saga flow counters (orders created / confirmed / cancelled)
- Kafka consumer lag
- JVM memory usage

![Grafana Dashboard](assets/grafana-dashboard.png)

### Distributed Traces

In Grafana, go to **Explore** and select the **Tempo** datasource.

Search by service name or trace ID from the service logs to see the full
request flow across all services:

![Tempo Trace](assets/tempo-trace.png)

### Prometheus Metrics

Open [http://localhost:9090](http://localhost:9090) and query:

```promql
orders_created_total
orders_confirmed_total
orders_cancelled_total
stock_reserved_total
payments_processed_total
```

## 7. View Service Logs

```bash
# Follow all service logs
docker compose logs -f order-service inventory-service payment-service notification-service

# Follow a specific service
docker compose logs -f order-service
```

## 8. Shutdown

```bash
cd infra
docker compose down -v   # -v removes volumes for a clean slate
```
