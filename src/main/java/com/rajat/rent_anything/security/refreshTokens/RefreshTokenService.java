package com.rajat.rent_anything.security.refreshTokens;

import com.rajat.rent_anything.common.enums.ErrorCode;
import com.rajat.rent_anything.common.exceptions.InvalidTokenException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Slf4j
public class RefreshTokenService {
    private final long refreshTokenExpiration;
    private final RefreshTokenRepository refreshTokenRepository;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository,
                               @Value("${jwt.refresh.token.expiration-days:30}") long refreshTokenExpiration) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTokenExpiration = refreshTokenExpiration;
    }

    @Transactional
    public RefreshTokenEntity createRefreshToken(Long userId) {
        RefreshTokenEntity token = new RefreshTokenEntity();
        token.setUserId(userId);
        token.setToken(UUID.randomUUID().toString());
        token.setCreatedAt(LocalDateTime.now());
        token.setExpiryAt(LocalDateTime.now().plusDays(refreshTokenExpiration));
        token.setRevoked(false);
        log.info("Created refresh token for userId {} with expiry at {}", userId, token.getExpiryAt());
        return refreshTokenRepository.save(token);
    }

    @Transactional
    public RefreshTokenEntity verifyAndRotateRefreshTokenIfFoundValid(String token) {
        var refreshToken = refreshTokenRepository.findByToken(token).orElseThrow(() -> new IllegalStateException("Invalid refresh token"));
        if (refreshToken.getExpiryAt().isBefore(LocalDateTime.now()) || refreshToken.isRevoked()) {
            throw new InvalidTokenException(ErrorCode.INVALID_TOKEN, "Refresh token is expired or revoked");
        }
        Long userId = refreshToken.getUserId();
        refreshTokenRepository.deleteByToken(token);
        log.info("Verified and revoked old refresh token for userId {} with token {}. Attempting to create new refresh token", userId, token);
        return createRefreshToken(userId);
    }

    @Transactional
    public void revokeRefreshToken(String token, Long userId) {
        var refreshToken = refreshTokenRepository.findByToken(token).orElseThrow(() -> new InvalidTokenException(ErrorCode.INVALID_TOKEN,"Invalid refresh token"));
        if(!refreshToken.getUserId().equals(userId)) {
            throw new InvalidTokenException(ErrorCode.INVALID_TOKEN, "Refresh token does not belong to the user");
        }
        refreshTokenRepository.deleteByToken(token);
        log.info("Revoked refresh token for userId {} with token {}", userId, token);
    }

    @Transactional
    public void revokeUserTokens(Long userId) {
        refreshTokenRepository.deleteByUserId(userId);
        log.info("Revoked all refresh tokens for userId {}", userId);
    }
}
