CREATE TABLE IF NOT EXISTS orders (
    id         uuid PRIMARY KEY,
    sku        text,
    quantity   int,
    amount     numeric(12, 2),
    status     text,
    created_at timestamptz DEFAULT now()
);
