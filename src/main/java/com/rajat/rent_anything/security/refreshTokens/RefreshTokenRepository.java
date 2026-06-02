package com.rajat.rent_anything.security.refreshTokens;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for managing refresh tokens.
 *
 * Provides database operations used during
 * token issuance, validation, and revocation.
 */
@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, Long> {

    /**
     * Retrieves a refresh token by its token value.
     *
     * @param token refresh token
     * @return matching token if found
     */
    Optional<RefreshTokenEntity> findByToken(String token);

    /**
     * Deletes a refresh token by its token value.
     *
     * Commonly used during logout or token rotation.
     *
     * @param token refresh token to delete
     */
    void deleteByToken(String token);

    /**
     * Deletes all refresh tokens associated with a user.
     *
     * Useful when invalidating all active sessions
     * for a particular user.
     *
     * @param userId user identifier
     */
    void deleteByUserId(Long userId);
}