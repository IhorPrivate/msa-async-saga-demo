CREATE TABLE IF NOT EXISTS event_log (
    id          bigserial PRIMARY KEY,
    source      text NOT NULL,
    routing_key text,
    payload     text,
    received_at timestamptz NOT NULL DEFAULT now()
);
