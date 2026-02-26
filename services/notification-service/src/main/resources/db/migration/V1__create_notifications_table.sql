CREATE TABLE notifications (
    id           UUID PRIMARY KEY,
    order_id     UUID NOT NULL,
    event_type   VARCHAR(128) NOT NULL,
    message      TEXT NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_notifications_order_id ON notifications(order_id);

CREATE TABLE processed_events (
    event_id     UUID PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
