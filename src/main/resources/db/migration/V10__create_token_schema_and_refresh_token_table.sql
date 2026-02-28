CREATE SCHEMA IF NOT EXISTS "token_schema";

CREATE TABLE token_schema.refresh_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token VARCHAR(255) NOT NULL UNIQUE,
    expiry_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_revoked BOOLEAN NOT NULL DEFAULT FALSE,
    FOREIGN KEY (user_id) REFERENCES user_schema.users(id) ON DELETE CASCADE
);

CREATE INDEX idx_refresh_tokens_user_id
ON token_schema.refresh_tokens (user_id)