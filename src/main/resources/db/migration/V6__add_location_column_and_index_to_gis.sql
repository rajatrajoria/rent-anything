ALTER TABLE item_schema.items
ADD COLUMN location geography(Point, 4326);

CREATE INDEX idx_items_location
ON item_schema.items
USING GIST (location);