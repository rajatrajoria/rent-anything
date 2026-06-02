CREATE TABLE item_schema.item_images (
    id BIGSERIAL PRIMARY KEY,
    item_id BIGINT NOT NULL,
    image_key VARCHAR(1000) NOT NULL,
    display_order INTEGER NOT NULL,
    is_thumbnail BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_item_images_item
        FOREIGN KEY(item_id)
        REFERENCES item_schema.items(id)
        ON DELETE CASCADE
);