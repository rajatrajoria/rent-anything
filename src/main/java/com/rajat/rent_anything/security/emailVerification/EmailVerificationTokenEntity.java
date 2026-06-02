package com.rajat.rent_anything.security.emailVerification;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
/**
 * Persistent representation of an email verification token.
 *
 * <h2>Purpose</h2>
 *
 * Stores one-time verification tokens used during the account
 * email verification process.
 *
 * A record in this table represents a pending verification request
 * for a specific user.
 *
 * <h2>Verification Workflow</h2>
 *
 * User Registers
 *       |
 *       v
 * Token Generated
 *       |
 *       v
 * Token Saved Here
 *       |
 *       v
 * Verification Email Sent
 *       |
 *       v
 * User Clicks Verification Link
 *       |
 *       v
 * Token Validated
 *       |
 *       v
 * Record Deleted
 *
 * <h2>Why Store Verification Tokens?</h2>
 *
 * Verification links must support:
 * <ul>
 *     <li>Expiration.</li>
 *     <li>One-time usage.</li>
 *     <li>Immediate invalidation.</li>
 *     <li>Resend operations.</li>
 * </ul>
 *
 * These requirements make a database-backed solution
 * more suitable than a stateless token approach.
 *
 * <h2>Lifecycle</h2>
 *
 * CREATED
 *    |
 *    v
 * ACTIVE
 *    |
 *    +------> EXPIRED
 *    |
 *    +------> VERIFIED
 *                 |
 *                 v
 *             DELETED
 *
 * <h2>Schema Design</h2>
 *
 * Tokens are stored separately from user records because:
 *
 * <ul>
 *     <li>Keeps user data focused on account information.</li>
 *     <li>Supports token lifecycle management.</li>
 *     <li>Allows independent cleanup and expiration.</li>
 *     <li>Maintains separation of concerns.</li>
 * </ul>
 */
@Getter
@Setter
@Entity
@Table(
        name = "email_verification_tokens",
        schema = "token_schema"
)
public class EmailVerificationTokenEntity {

    /**
     * Primary key.
     *
     * Database-generated unique identifier.
     *
     * This value is used only for persistence purposes
     * and is never exposed to clients.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Identifier of the user being verified.
     *
     * Design Decision:
     *
     * The entity stores only the user ID rather than
     * a JPA relationship to the User entity.
     *
     * Benefits:
     * <ul>
     *     <li>Reduces unnecessary entity loading.</li>
     *     <li>Avoids accidental joins.</li>
     *     <li>Keeps token storage lightweight.</li>
     *     <li>Maintains loose coupling.</li>
     * </ul>
     *
     * Future Enhancement:
     *
     * If navigation from token -> user becomes common,
     * a @ManyToOne relationship may be introduced.
     */
    private Long userId;

    /**
     * Unique verification token sent to the user.
     *
     * Example:
     *
     * 550e8400-e29b-41d4-a716-446655440000
     *
     * This token is included in verification URLs and
     * acts as proof that the user has access to the email account.
     *
     * Security Requirements:
     * <ul>
     *     <li>Must be difficult to guess.</li>
     *     <li>Must be unique.</li>
     *     <li>Must expire.</li>
     *     <li>Must be single-use.</li>
     * </ul>
     */
    private String token;

    /**
     * Timestamp indicating when the token was created.
     *
     * Common Uses:
     * <ul>
     *     <li>Auditing.</li>
     *     <li>Troubleshooting.</li>
     *     <li>Token age calculations.</li>
     *     <li>Security investigations.</li>
     * </ul>
     */
    private LocalDateTime createdAt;

    /**
     * Timestamp after which the token becomes invalid.
     *
     * Security Purpose:
     * Prevents verification links from remaining valid indefinitely.
     *
     * Validation Rule:
     *
     * Current Time > expiresAt
     *
     * => Token is rejected.
     *
     * Example:
     *
     * Created: 10:00 AM
     * Expiration: 11:00 AM
     *
     * Any verification attempt after 11:00 AM fails.
     */
    private LocalDateTime expiresAt;

    /**
     * Timestamp tracking the most recent modification.
     *
     * Useful for:
     * <ul>
     *     <li>Auditing.</li>
     *     <li>Operational monitoring.</li>
     *     <li>Future token update workflows.</li>
     * </ul>
     *
     * Current Usage:
     * Primarily maintained for consistency with other entities.
     *
     * Future Use Cases:
     * - Token renewal tracking.
     * - Administrative updates.
     * - Security audits.
     */
    private LocalDateTime updatedAt;
}