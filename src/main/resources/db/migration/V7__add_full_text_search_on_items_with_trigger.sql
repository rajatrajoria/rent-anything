ALTER TABLE item_schema.items
ADD COLUMN search_vector tsvector;

CREATE INDEX idx_items_search
ON item_schema.items
USING GIN (search_vector);

CREATE OR REPLACE FUNCTION item_schema.item_search_vector_update()
RETURNS trigger AS $$
BEGIN
  NEW.search_vector :=
    to_tsvector('english',
      coalesce(NEW.title,'') || ' ' ||
      coalesce(NEW.description,'')
    );
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS item_search_vector_trigger
ON item_schema.items;

CREATE TRIGGER item_search_vector_trigger
BEFORE INSERT OR UPDATE
ON item_schema.items
FOR EACH ROW
EXECUTE FUNCTION item_schema.item_search_vector_update();