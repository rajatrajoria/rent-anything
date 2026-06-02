package com.rajat.rent_anything.security.refreshTokens;

import com.rajat.rent_anything.common.enums.ErrorCode;
import com.rajat.rent_anything.common.exceptions.InvalidTokenException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Service responsible for managing refresh tokens.
 *
 * Refresh tokens are used to obtain new access tokens
 * without requiring the user to log in again.
 *
 * Security Features:
 * - Configurable token expiration.
 * - Token rotation on successful refresh.
 * - Token revocation during logout.
 * - Ability to revoke all user sessions.
 */
@Service
@Slf4j
public class RefreshTokenService {

    /**
     * Refresh token validity duration in days.
     */
    private final long refreshTokenExpiration;

    /**
     * Repository for storing and managing refresh tokens.
     */
    private final RefreshTokenRepository refreshTokenRepository;

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            @Value("${jwt.refresh.token.expiration-days:30}")
            long refreshTokenExpiration
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTokenExpiration = refreshTokenExpiration;
    }

    /**
     * Creates a new refresh token for a user.
     *
     * @param userId user identifier
     * @return persisted refresh token entity
     */
    @Transactional
    public RefreshTokenEntity createRefreshToken(Long userId) {

        RefreshTokenEntity token = new RefreshTokenEntity();

        token.setUserId(userId);

        // Generate a unique refresh token.
        token.setToken(UUID.randomUUID().toString());

        token.setCreatedAt(LocalDateTime.now());

        // Token remains valid for the configured duration.
        token.setExpiryAt(
                LocalDateTime.now().plusDays(refreshTokenExpiration)
        );

        token.setRevoked(false);

        log.info(
                "Created refresh token for userId {} with expiry at {}",
                userId,
                token.getExpiryAt()
        );

        return refreshTokenRepository.save(token);
    }

    /**
     * Validates a refresh token and performs token rotation.
     *
     * Token Rotation:
     * - Old token is invalidated.
     * - New refresh token is generated.
     *
     * This reduces the risk of token reuse if a refresh token
     * is ever compromised.
     *
     * @param token refresh token
     * @return newly generated refresh token
     * @throws InvalidTokenException if token is expired or revoked
     */
    @Transactional
    public RefreshTokenEntity verifyAndRotateRefreshTokenIfFoundValid(
            String token
    ) {

        var refreshToken = refreshTokenRepository.findByToken(token)
                .orElseThrow(() ->
                        new IllegalStateException("Invalid refresh token")
                );

        if (refreshToken.getExpiryAt().isBefore(LocalDateTime.now())
                || refreshToken.isRevoked()) {

            throw new InvalidTokenException(
                    ErrorCode.INVALID_TOKEN,
                    "Refresh token is expired or revoked"
            );
        }

        Long userId = refreshToken.getUserId();

        // Remove old token before issuing a new one.
        refreshTokenRepository.deleteByToken(token);

        log.info(
                "Verified and revoked old refresh token for userId {}. Creating replacement token.",
                userId
        );

        return createRefreshToken(userId);
    }

    /**
     * Revokes a specific refresh token.
     *
     * Typically used during logout operations.
     *
     * Validation ensures that the token belongs
     * to the requesting user.
     *
     * @param token refresh token
     * @param userId user identifier
     */
    @Transactional
    public void revokeRefreshToken(String token, Long userId) {

        var refreshToken = refreshTokenRepository.findByToken(token)
                .orElseThrow(() ->
                        new InvalidTokenException(
                                ErrorCode.INVALID_TOKEN,
                                "Invalid refresh token"
                        )
                );

        if (!refreshToken.getUserId().equals(userId)) {
            throw new InvalidTokenException(
                    ErrorCode.INVALID_TOKEN,
                    "Refresh token does not belong to the user"
            );
        }

        refreshTokenRepository.deleteByToken(token);

        log.info(
                "Revoked refresh token for userId {}",
                userId
        );
    }

    /**
     * Revokes all refresh tokens associated with a user.
     *
     * Useful when:
     * - User changes password.
     * - User logs out from all devices.
     * - Security-related account actions occur.
     *
     * @param userId user identifier
     */
    @Transactional
    public void revokeUserTokens(Long userId) {

        refreshTokenRepository.deleteByUserId(userId);

        log.info(
                "Revoked all refresh tokens for userId {}",
                userId
        );
    }
}