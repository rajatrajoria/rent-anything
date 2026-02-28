CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE booking_schema.bookings
ADD CONSTRAINT no_overlapping_bookings
EXCLUDE USING gist (
    item_id WITH =,
    daterange(start_date, end_date, '[]') WITH &&
)
WHERE (status IN ('PENDING','CONFIRMED'));