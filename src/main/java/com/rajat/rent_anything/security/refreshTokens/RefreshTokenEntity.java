package com.rajat.rent_anything.security.refreshTokens;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Entity representing a refresh token.
 *
 * Refresh tokens are used to obtain new access tokens
 * without requiring the user to log in again.
 *
 * Unlike access tokens, refresh tokens are stored in the database
 * so they can be validated, revoked, and managed securely.
 */
@Getter
@Setter
@Entity
@Table(name = "refresh_tokens", schema = "token_schema")
public class RefreshTokenEntity {

    /**
     * Unique identifier for the refresh token record.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * ID of the user who owns this refresh token.
     */
    private Long userId;

    /**
     * Unique refresh token value issued to the user.
     */
    private String token;

    /**
     * Timestamp indicating when the token was created.
     */
    private LocalDateTime createdAt;

    /**
     * Timestamp after which the refresh token becomes invalid.
     */
    private LocalDateTime expiryAt;

    /**
     * Indicates whether the token has been explicitly revoked.
     *
     * Revoked tokens cannot be used even if they have not expired.
     */
    private boolean isRevoked;
}