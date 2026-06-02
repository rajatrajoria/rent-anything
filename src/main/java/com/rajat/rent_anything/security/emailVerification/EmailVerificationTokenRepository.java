package com.rajat.rent_anything.security.emailVerification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for managing email verification tokens.
 *
 * Provides database operations for creating, retrieving,
 * and deleting verification tokens used during the
 * email verification workflow.
 */
@Repository
public interface EmailVerificationTokenRepository
        extends JpaRepository<EmailVerificationTokenEntity, Long> {

    /**
     * Retrieves a verification token record by its token value.
     *
     * Used during email verification when a user clicks
     * the verification link.
     *
     * @param token verification token
     * @return matching token record if found
     */
    Optional<EmailVerificationTokenEntity> findByToken(String token);

    /**
     * Retrieves the verification token associated with a user.
     *
     * Primarily used when resending verification emails.
     *
     * @param userId user identifier
     * @return user's verification token if present
     */
    Optional<EmailVerificationTokenEntity> findByUserId(Long userId);

    /**
     * Deletes a verification token record.
     *
     * Typically used after successful verification
     * or when replacing an expired token.
     *
     * @param entity token entity to delete
     */
    void delete(EmailVerificationTokenEntity entity);
}