package com.rajat.rent_anything.security.emailVerification;

import com.rajat.rent_anything.common.enums.ErrorCode;
import com.rajat.rent_anything.common.exceptions.InvalidSecurityOperation;
import com.rajat.rent_anything.common.exceptions.InvalidTokenException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Handles email verification token lifecycle management.
 *
 * <h2>Purpose</h2>
 * <p>
 * This service is responsible for:
 * <ul>
 *     <li>Generating email verification tokens.</li>
 *     <li>Resending verification tokens.</li>
 *     <li>Validating verification tokens.</li>
 *     <li>Preventing reuse of verification tokens.</li>
 * </ul>
 *
 * <h2>Why Email Verification?</h2>
 * <p>
 * Email verification ensures that:
 * <ul>
 *     <li>The user owns the email address they registered with.</li>
 *     <li>Fake or mistyped email addresses are detected.</li>
 *     <li>Important future communications can reach the user.</li>
 *     <li>Account abuse and spam registrations are reduced.</li>
 * </ul>
 *
 * <h2>Verification Flow</h2>
 * <p>
 * User Registration
 *       |
 *       v
 * Create Verification Token
 *       |
 *       v
 * Save Token in Database
 *       |
 *       v
 * Send Verification Email
 *       |
 *       v
 * User Clicks Verification Link
 *       |
 *       v
 * Verify Token
 *       |
 *       v
 * Mark User As Verified
 *       |
 *       v
 * Delete Token
 *
 * <h2>Why Store Tokens in Database?</h2>
 * <p>
 * Unlike authentication JWTs, email verification tokens are persisted
 * in the database because:
 *
 * <ul>
 *     <li>Tokens must be revocable.</li>
 *     <li>Tokens should be invalidated after use.</li>
 *     <li>Token expiration must be tracked.</li>
 *     <li>Verification links are one-time use.</li>
 * </ul>
 *
 * <h2>Token Lifecycle</h2>
 * <p>
 * Generated -> Active -> Verified/Expired -> Deleted
 *
 * <h2>Security Considerations</h2>
 * <p>
 * Verification tokens:
 * <ul>
 *     <li>Are random and difficult to guess.</li>
 *     <li>Expire after a configurable period.</li>
 *     <li>Can only be used once.</li>
 *     <li>Are removed immediately after successful verification.</li>
 * </ul>
 */
@Slf4j
@Service
public class EmailVerificationService {

    /**
     * Repository responsible for storing and retrieving
     * email verification tokens.
     * <p>
     * Acts as the persistence layer for verification workflows.
     */
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;

    /**
     * Token validity duration in minutes.
     * <p>
     * Configurable through application properties.
     * <p>
     * Example:
     * 60 = token expires one hour after creation.
     * <p>
     * Why expire tokens?
     * - Limits attack window.
     * - Prevents indefinitely valid links.
     * - Encourages timely verification.
     */
    private final long expiryMinutes;

    public EmailVerificationService(
            @Value("${jwt.email.verification.expiration-minutes}")
            long expiryMinutes,
            EmailVerificationTokenRepository emailVerificationTokenRepository) {

        this.emailVerificationTokenRepository = emailVerificationTokenRepository;
        this.expiryMinutes = expiryMinutes;
    }

    /**
     * Creates a new email verification token for a user.
     *
     * <h2>Workflow</h2>
     * <p>
     * 1. Generate a cryptographically random token.
     * 2. Associate token with user.
     * 3. Set creation and expiration timestamps.
     * 4. Persist token.
     * 5. Return token for email delivery.
     *
     * <h2>Why UUID?</h2>
     * <p>
     * UUIDs provide:
     * <ul>
     *     <li>High uniqueness guarantees.</li>
     *     <li>Large search space.</li>
     *     <li>Low collision probability.</li>
     *     <li>No dependency on user identifiers.</li>
     * </ul>
     * <p>
     * Example Token:
     * 550e8400-e29b-41d4-a716-446655440000
     *
     * <h2>Transactional Behavior</h2>
     * <p>
     * The entire token creation process is executed
     * within a database transaction.
     * <p>
     * This guarantees that either:
     * - The token is fully persisted, or
     * - No partial changes occur.
     *
     * @param userId user receiving verification email
     * @return generated verification token
     */
    @Transactional
    public String createEmailVerificationToken(Long userId) {

        EmailVerificationTokenEntity entity = new EmailVerificationTokenEntity();

        entity.setUserId(userId);

        /**
         * Generate a unique verification token.
         */
        entity.setToken(UUID.randomUUID().toString());

        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());

        /**
         * Calculate expiration timestamp.
         */
        entity.setExpiresAt(
                LocalDateTime.now().plusMinutes(expiryMinutes)
        );

        emailVerificationTokenRepository.save(entity);

        return entity.getToken();
    }

    /**
     * Generates a replacement verification token.
     *
     * <h2>Business Rule</h2>
     * <p>
     * Users cannot continuously request new verification tokens
     * while a previously issued token is still valid.
     * <p>
     * Why?
     *
     * <ul>
     *     <li>Prevents email spam.</li>
     *     <li>Reduces unnecessary token creation.</li>
     *     <li>Simplifies verification flow.</li>
     *     <li>Avoids multiple active tokens.</li>
     * </ul>
     *
     * <h2>Workflow</h2>
     * <p>
     * 1. Retrieve existing token.
     * 2. Verify token has expired.
     * 3. Remove old token.
     * 4. Generate replacement token.
     * 5. Return new token.
     *
     * @param userId target user
     * @return newly generated verification token
     * @throws InvalidTokenException    when no verification token exists
     * @throws InvalidSecurityOperation when current token is still active
     */
    @Transactional
    public String resendEmailVerificationToken(Long userId) {

        EmailVerificationTokenEntity entity =
                emailVerificationTokenRepository.findByUserId(userId)
                        .orElseThrow(() ->
                                new InvalidTokenException(
                                        ErrorCode.INVALID_TOKEN,
                                        "No existing verification token found for user ID: " + userId
                                )
                        );

        /**
         * Prevent multiple valid tokens from existing
         * simultaneously for the same user.
         */
        if (entity.getExpiresAt().isAfter(LocalDateTime.now())) {

            throw new InvalidSecurityOperation(
                    ErrorCode.INVALID_SECURITY_OPERATION,
                    "Existing verification token is still valid. Please wait before requesting a new one."
            );
        }

        /**
         * Remove expired token before creating a new one.
         */
        emailVerificationTokenRepository.delete(entity);

        return createEmailVerificationToken(userId);
    }

    /**
     * Verifies a user's email using a verification token.
     *
     * <h2>Verification Workflow</h2>
     * <p>
     * 1. Locate token.
     * 2. Verify token exists.
     * 3. Verify token has not expired.
     * 4. Extract associated user.
     * 5. Delete token.
     * 6. Return user ID.
     *
     * <h2>Why Delete After Verification?</h2>
     * <p>
     * Email verification links are intended to be
     * one-time-use credentials.
     * <p>
     * Deleting the token:
     * <ul>
     *     <li>Prevents replay attacks.</li>
     *     <li>Prevents accidental reuse.</li>
     *     <li>Ensures verification can occur only once.</li>
     * </ul>
     *
     * <h2>Token Expiration Check</h2>
     * <p>
     * Expired tokens are rejected immediately.
     * <p>
     * This prevents:
     * <ul>
     *     <li>Old leaked links from being used.</li>
     *     <li>Unexpected verification of stale accounts.</li>
     *     <li>Long-lived attack opportunities.</li>
     * </ul>
     *
     * @param token verification token received from email link
     * @return verified user's ID
     * @throws InvalidTokenException when token is missing, invalid, or expired
     */
    @Transactional
    public Long verifyEmail(String token) {

        log.info("Attempting to verify email with token: {}", token);

        EmailVerificationTokenEntity entity =
                emailVerificationTokenRepository.findByToken(token)
                        .orElseThrow(() ->
                                new InvalidTokenException(
                                        ErrorCode.INVALID_TOKEN,
                                        "Invalid verification token"
                                )
                        );

        if (entity.getExpiresAt().isBefore(LocalDateTime.now())) {

            throw new InvalidTokenException(
                    ErrorCode.INVALID_TOKEN,
                    "Verification token has expired"
            );
        }

        /**
         * One-time-use token invalidation.
         */
        emailVerificationTokenRepository.delete(entity);

        log.info(
                "Email verification successful for user with ID: {}",
                entity.getUserId()
        );

        return entity.getUserId();
    }
}