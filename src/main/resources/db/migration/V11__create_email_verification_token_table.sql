CREATE SCHEMA IF NOT EXISTS token_schema;

CREATE TABLE token_schema.email_verification_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT fk_evt_user FOREIGN KEY (user_id) REFERENCES user_schema.users(id) ON DELETE CASCADE
);

CREATE INDEX idx_email_verification_user_id
ON token_schema.email_verification_tokens (user_id);