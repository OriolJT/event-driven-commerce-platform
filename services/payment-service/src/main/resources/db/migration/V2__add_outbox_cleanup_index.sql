-- Index for outbox cleanup: efficiently find old published rows to delete
CREATE INDEX idx_outbox_cleanup ON outbox_events(created_at) WHERE published = true;
