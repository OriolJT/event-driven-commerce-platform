# Runbook

## Prerequisites

- Java 21 (Temurin)
- Maven 3.9+
- Docker Desktop with Docker Compose v2

## Build

```bash
# From project root
mvn clean install -DskipTests
```

## Docker Images

```bash
mvn -pl services/order-service,services/payment-service,services/inventory-service,services/notification-service jib:dockerBuild
```

## Start Local Stack

```bash
cd infra
docker compose up -d
```

Wait for all services to be healthy:
```bash
docker compose ps
```

## API Examples

### Create Order
```bash
curl -X POST http://localhost:8081/api/orders \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: order-001" \
  -d '{
    "customerId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
    "items": [
      {"productId": "11111111-1111-1111-1111-111111111111", "quantity": 2, "unitPrice": 29.99},
      {"productId": "22222222-2222-2222-2222-222222222222", "quantity": 1, "unitPrice": 89.99}
    ],
    "currency": "EUR"
  }'
```

### Check Order Status
```bash
curl http://localhost:8081/api/orders/{orderId}
```

### Verify Idempotency
```bash
# Same request with same key → same response
curl -X POST http://localhost:8081/api/orders \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: order-001" \
  -d '{"customerId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", "items": [{"productId": "11111111-1111-1111-1111-111111111111", "quantity": 2, "unitPrice": 29.99}, {"productId": "22222222-2222-2222-2222-222222222222", "quantity": 1, "unitPrice": 89.99}], "currency": "EUR"}'
```

## Observability

### Metrics
- Prometheus: http://localhost:9090
- Query examples:
  - `orders_created_total`
  - `orders_confirmed_total`
  - `orders_cancelled_total`
  - `payments_processed_total`
  - `stock_reserved_total`

### Dashboards
- Grafana: http://localhost:3000 (admin/admin)
- "Platform Service Overview" dashboard pre-provisioned

### Traces
- Grafana → Explore → Select "Tempo" datasource
- Search by traceId from service logs

### Health Checks
```bash
curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health
curl http://localhost:8083/actuator/health
curl http://localhost:8084/actuator/health
```

## Troubleshooting

### Service won't start
- Check Postgres is healthy: `docker compose logs postgres`
- Check Kafka is healthy: `docker compose logs kafka`
- Check service logs: `docker compose logs order-service`

### Orders stuck in PENDING
- Check inventory-service logs for stock reservation
- Verify Kafka topics: `docker exec platform-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list`
- Check consumer groups: `docker exec platform-kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --list`

### Dead Letter Topics
- Check DLT messages: `docker exec platform-kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic order-events.DLT --from-beginning`

## Running Tests

```bash
# All tests (Docker must be running for Testcontainers)
mvn test

# Specific service
mvn test -pl services/order-service -am
mvn test -pl services/inventory-service -am
mvn test -pl services/payment-service -am
```

## Shutdown

```bash
cd infra
docker compose down -v  # -v removes volumes
```
