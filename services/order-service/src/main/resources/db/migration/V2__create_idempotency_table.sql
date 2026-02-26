CREATE TABLE idempotency_keys (
    key           VARCHAR(64) PRIMARY KEY,
    order_id      UUID NOT NULL,
    request_hash  VARCHAR(64) NOT NULL,
    response_body JSONB NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
