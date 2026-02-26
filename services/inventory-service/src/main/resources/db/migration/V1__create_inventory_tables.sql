CREATE TABLE products (
    id    UUID PRIMARY KEY,
    name  VARCHAR(255) NOT NULL,
    stock INT NOT NULL DEFAULT 0
);

CREATE TABLE reservations (
    id         UUID PRIMARY KEY,
    order_id   UUID NOT NULL,
    product_id UUID NOT NULL REFERENCES products(id),
    quantity   INT NOT NULL,
    status     VARCHAR(32) NOT NULL DEFAULT 'RESERVED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_reservations_order_id ON reservations(order_id);

CREATE TABLE processed_events (
    event_id     UUID PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE outbox_events (
    id             UUID PRIMARY KEY,
    aggregate_type VARCHAR(128) NOT NULL,
    aggregate_id   UUID NOT NULL,
    event_type     VARCHAR(128) NOT NULL,
    payload        JSONB NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    published      BOOLEAN NOT NULL DEFAULT false
);

CREATE INDEX idx_outbox_unpublished ON outbox_events(published) WHERE published = false;
