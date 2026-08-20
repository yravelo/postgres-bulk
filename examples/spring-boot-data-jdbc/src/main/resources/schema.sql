CREATE TABLE IF NOT EXISTS jdbc_product (
    id uuid PRIMARY KEY,
    sku text NOT NULL UNIQUE,
    category text NOT NULL,
    name text NOT NULL,
    price numeric(19, 2) NOT NULL,
    shipping_city text
);
