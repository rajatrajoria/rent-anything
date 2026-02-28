CREATE SCHEMA IF NOT EXISTS item_schema;

CREATE TABLE item_schema.items (
    id BIGSERIAL PRIMARY KEY,
    owner_id BIGINT NOT NULL,
    zone_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,

    title VARCHAR(255) NOT NULL,
    description TEXT,

    price_per_day DOUBLE PRECISION NOT NULL,
    deposit_amount DOUBLE PRECISION NOT NULL,

    status VARCHAR(50) NOT NULL,

    available_from DATE NOT NULL,
    available_to DATE NOT NULL,

    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);