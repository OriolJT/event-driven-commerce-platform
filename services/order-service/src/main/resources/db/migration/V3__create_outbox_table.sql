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
