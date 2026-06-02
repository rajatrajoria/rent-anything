package com.rajat.rent_anything.security.passwordReset;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Entity representing a password reset token.
 *
 * Stores temporary tokens used during the password
 * reset workflow. Each token is associated with a user
 * and remains valid until its expiration time.
 *
 * Typical Flow:
 * User requests password reset
 *      |
 *      v
 * Token generated and stored
 *      |
 *      v
 * Email sent to user
 *      |
 *      v
 * User submits token and new password
 *      |
 *      v
 * Token validated and removed
 */
@Getter
@Setter
@Entity
@Table(name = "password_reset_tokens", schema = "token_schema")
public class PasswordResetTokenEntity {

    /**
     * Unique identifier for the token record.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * ID of the user who requested the password reset.
     */
    private Long userId;

    /**
     * Unique reset token sent to the user's email.
     */
    private String token;

    /**
     * Timestamp after which the token becomes invalid.
     */
    private LocalDateTime expiryDate;

    /**
     * Timestamp indicating when the token was created.
     */
    private LocalDateTime createdAt;
}