CREATE TABLE IF NOT EXISTS outbox (
    id           uuid PRIMARY KEY,
    aggregate_id uuid        NOT NULL,
    exchange     text        NOT NULL,
    routing_key  text        NOT NULL,
    payload      text        NOT NULL,
    created_at   timestamptz NOT NULL DEFAULT now(),
    published_at timestamptz NULL
);

-- Частковий індекс: релей читає лише неопубліковані рядки, тож індекс лишається малим
CREATE INDEX IF NOT EXISTS idx_outbox_pending ON outbox (created_at) WHERE published_at IS NULL;

CREATE TABLE IF NOT EXISTS processed_messages (
    message_id text NOT NULL,
    consumer   text NOT NULL,
    PRIMARY KEY (message_id, consumer)
);
