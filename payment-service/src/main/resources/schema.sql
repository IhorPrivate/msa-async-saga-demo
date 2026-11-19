-- UNIQUE(order_id) дає природну ідемпотентність open().
CREATE TABLE IF NOT EXISTS payments (
    id       uuid PRIMARY KEY,
    order_id uuid UNIQUE NOT NULL,
    amount   numeric(12,2) NOT NULL,
    status   text NOT NULL
);
