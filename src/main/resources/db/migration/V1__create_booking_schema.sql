CREATE SCHEMA IF NOT EXISTS booking_schema;

CREATE TABLE booking_schema.bookings (
    id BIGSERIAL PRIMARY KEY,
    item_id BIGINT NOT NULL,
    renter_id BIGINT NOT NULL,
    owner_id BIGINT NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    amount DOUBLE PRECISION,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);