CREATE TABLE IF NOT EXISTS stock (
    sku       text PRIMARY KEY,
    available int NOT NULL
);

-- Навмисно без UNIQUE(order_id): на кроці S5 буде видно подвійне списання.
CREATE TABLE IF NOT EXISTS reservations (
    id       uuid PRIMARY KEY,
    order_id uuid NOT NULL,
    sku      text NOT NULL,
    quantity int  NOT NULL,
    status   text NOT NULL
);

INSERT INTO stock (sku, available) VALUES ('COFFEE', 10), ('TEA', 0) ON CONFLICT DO NOTHING;
