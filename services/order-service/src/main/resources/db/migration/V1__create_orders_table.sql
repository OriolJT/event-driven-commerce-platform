CREATE TABLE orders (
    id              UUID PRIMARY KEY,
    customer_id     UUID NOT NULL,
    status          VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    total_amount    NUMERIC(12, 2) NOT NULL,
    currency        VARCHAR(3) NOT NULL DEFAULT 'EUR',
    idempotency_key VARCHAR(64) UNIQUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE order_items (
    id          UUID PRIMARY KEY,
    order_id    UUID NOT NULL REFERENCES orders(id),
    product_id  UUID NOT NULL,
    quantity    INT NOT NULL,
    unit_price  NUMERIC(12, 2) NOT NULL
);

CREATE INDEX idx_order_items_order_id ON order_items(order_id);
