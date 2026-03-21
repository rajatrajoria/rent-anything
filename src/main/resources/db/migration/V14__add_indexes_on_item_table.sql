-- Partial index
CREATE INDEX idx_items_active
ON item_schema.items (id)
WHERE status = 'ACTIVE';

-- Combined geo + status
CREATE INDEX idx_items_active_location
ON item_schema.items USING GIST (location)
WHERE status = 'ACTIVE';