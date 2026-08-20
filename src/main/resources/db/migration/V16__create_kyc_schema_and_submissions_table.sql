CREATE SCHEMA IF NOT EXISTS "kyc_schema";

CREATE TABLE kyc_schema.kyc_submissions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    legal_name VARCHAR(255) NOT NULL,
    date_of_birth DATE NOT NULL,
    address_line1 VARCHAR(255) NOT NULL,
    address_line2 VARCHAR(255),
    city VARCHAR(255) NOT NULL,
    state VARCHAR(255) NOT NULL,
    postal_code VARCHAR(20) NOT NULL,
    country VARCHAR(2) NOT NULL,
    id_document_type VARCHAR(50) NOT NULL,
    id_document_image_key VARCHAR(1000) NOT NULL,
    selfie_image_key VARCHAR(1000) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    rejection_reason VARCHAR(1000),
    reviewed_by BIGINT,
    reviewed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_kyc_submissions_user
        FOREIGN KEY(user_id)
        REFERENCES user_schema.users(id)
        ON DELETE CASCADE
);

CREATE INDEX idx_kyc_submissions_status ON kyc_schema.kyc_submissions(status);
