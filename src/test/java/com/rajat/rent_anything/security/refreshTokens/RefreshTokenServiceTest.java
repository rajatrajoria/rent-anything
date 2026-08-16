package com.rajat.rent_anything.security.refreshTokens;

import com.rajat.rent_anything.common.exceptions.InvalidTokenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private RefreshTokenService refreshTokenService;

    private static final Long USER_ID = 1L;
    private static final long EXPIRATION_DAYS = 30;

    @BeforeEach
    void setUp() {
        refreshTokenService = new RefreshTokenService(refreshTokenRepository, EXPIRATION_DAYS);
    }

    private RefreshTokenEntity tokenEntity(String token, Long userId, LocalDateTime expiryAt, boolean revoked) {
        RefreshTokenEntity entity = new RefreshTokenEntity();
        entity.setToken(token);
        entity.setUserId(userId);
        entity.setExpiryAt(expiryAt);
        entity.setRevoked(revoked);
        entity.setCreatedAt(LocalDateTime.now());
        return entity;
    }

    @Test
    void createRefreshToken_savesEntityWithFutureExpiryAndNotRevoked() {
        when(refreshTokenRepository.save(any(RefreshTokenEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        RefreshTokenEntity result = refreshTokenService.createRefreshToken(USER_ID);

        assertThat(result.getUserId()).isEqualTo(USER_ID);
        assertThat(result.getToken()).isNotBlank();
        assertThat(result.isRevoked()).isFalse();
        assertThat(result.getExpiryAt()).isAfter(LocalDateTime.now());
    }

    @Test
    void verifyAndRotate_validToken_deletesOldAndCreatesNewToken() {
        RefreshTokenEntity existing = tokenEntity("old-token", USER_ID, LocalDateTime.now().plusDays(1), false);
        when(refreshTokenRepository.findByToken("old-token")).thenReturn(Optional.of(existing));
        when(refreshTokenRepository.save(any(RefreshTokenEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        RefreshTokenEntity result = refreshTokenService.verifyAndRotateRefreshTokenIfFoundValid("old-token");

        verify(refreshTokenRepository).deleteByToken("old-token");
        assertThat(result.getUserId()).isEqualTo(USER_ID);
        assertThat(result.getToken()).isNotEqualTo("old-token");
    }

    @Test
    void verifyAndRotate_tokenDoesNotExist_throwsIllegalStateException() {
        when(refreshTokenRepository.findByToken("missing")).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class,
                () -> refreshTokenService.verifyAndRotateRefreshTokenIfFoundValid("missing"));
    }

    @Test
    void verifyAndRotate_expiredToken_throwsInvalidTokenException() {
        RefreshTokenEntity expired = tokenEntity("expired", USER_ID, LocalDateTime.now().minusDays(1), false);
        when(refreshTokenRepository.findByToken("expired")).thenReturn(Optional.of(expired));

        assertThrows(InvalidTokenException.class,
                () -> refreshTokenService.verifyAndRotateRefreshTokenIfFoundValid("expired"));

        verify(refreshTokenRepository, never()).deleteByToken(any());
    }

    @Test
    void verifyAndRotate_revokedToken_throwsInvalidTokenException() {
        RefreshTokenEntity revoked = tokenEntity("revoked", USER_ID, LocalDateTime.now().plusDays(1), true);
        when(refreshTokenRepository.findByToken("revoked")).thenReturn(Optional.of(revoked));

        assertThrows(InvalidTokenException.class,
                () -> refreshTokenService.verifyAndRotateRefreshTokenIfFoundValid("revoked"));
    }

    @Test
    void revokeRefreshToken_ownedByCaller_deletesToken() {
        RefreshTokenEntity entity = tokenEntity("token", USER_ID, LocalDateTime.now().plusDays(1), false);
        when(refreshTokenRepository.findByToken("token")).thenReturn(Optional.of(entity));

        refreshTokenService.revokeRefreshToken("token", USER_ID);

        verify(refreshTokenRepository, times(1)).deleteByToken("token");
    }

    @Test
    void revokeRefreshToken_tokenDoesNotExist_throwsInvalidTokenException() {
        when(refreshTokenRepository.findByToken("token")).thenReturn(Optional.empty());

        assertThrows(InvalidTokenException.class,
                () -> refreshTokenService.revokeRefreshToken("token", USER_ID));
    }

    @Test
    void revokeRefreshToken_belongsToDifferentUser_throwsInvalidTokenException() {
        RefreshTokenEntity entity = tokenEntity("token", 999L, LocalDateTime.now().plusDays(1), false);
        when(refreshTokenRepository.findByToken("token")).thenReturn(Optional.of(entity));

        assertThrows(InvalidTokenException.class,
                () -> refreshTokenService.revokeRefreshToken("token", USER_ID));

        verify(refreshTokenRepository, never()).deleteByToken(any());
    }

    @Test
    void revokeUserTokens_deletesAllTokensForUser() {
        refreshTokenService.revokeUserTokens(USER_ID);

        verify(refreshTokenRepository).deleteByUserId(USER_ID);
    }
}
