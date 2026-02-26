# Failure Scenarios

## Duplicate Delivery
**Scenario**: Kafka delivers the same message twice (at-least-once semantics).
**Mitigation**: Every consumer checks the `processed_events` table before processing. If `eventId` exists, the message is skipped. The insert is part of the same transaction as the business operation.

## Consumer Crash Mid-Processing
**Scenario**: Consumer crashes after processing but before committing the Kafka offset.
**Mitigation**: Manual acknowledgment (`ack-mode: record`) ensures the offset is only committed after the listener returns. On restart, the message is redelivered. Idempotency via `processed_events` ensures no double processing.

## Kafka Unavailable
**Scenario**: Kafka is down when a service tries to publish an event.
**Mitigation**: The outbox pattern decouples event publishing from the business transaction. Events are written to the `outbox_events` table and will be published when Kafka becomes available. The publisher retries every 500ms.

## Payment Timeout / Failure
**Scenario**: Payment processing fails or the payment provider is unreachable.
**Mitigation**: Payment service publishes `PaymentFailed` event. Order service receives it, cancels the order, and emits `StockReleaseRequested` to trigger inventory compensation.

## Stock Insufficient
**Scenario**: Not enough stock to fulfill the order.
**Mitigation**: Inventory service publishes `StockRejected`. Order service cancels the order. No stock release needed since nothing was reserved.

## Outbox Publisher Crash
**Scenario**: The outbox publisher crashes after sending to Kafka but before marking the row as published.
**Mitigation**: The event will be re-sent on next poll. Downstream consumers handle duplicates via idempotency.

## Database Failure
**Scenario**: PostgreSQL is down.
**Mitigation**: All services will fail health checks. Kafka consumers will stop processing (no DB to write to). Events remain in Kafka until the database recovers. Kubernetes/Docker health checks can restart services.

## Poison Messages
**Scenario**: A malformed message cannot be deserialized or processed.
**Mitigation**: `DefaultErrorHandler` with `FixedBackOff(1000, 3)` retries 3 times, then routes to `<topic>.DLT` (Dead Letter Topic) for manual inspection.
