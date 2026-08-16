package com.rajat.rent_anything.security.emailVerification;

import com.rajat.rent_anything.common.exceptions.InvalidSecurityOperation;
import com.rajat.rent_anything.common.exceptions.InvalidTokenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

    @Mock
    private EmailVerificationTokenRepository repository;

    private EmailVerificationService service;

    private static final Long USER_ID = 1L;
    private static final long EXPIRY_MINUTES = 5;

    @BeforeEach
    void setUp() {
        service = new EmailVerificationService(EXPIRY_MINUTES, repository);
    }

    private EmailVerificationTokenEntity tokenEntity(String token, LocalDateTime expiresAt) {
        EmailVerificationTokenEntity entity = new EmailVerificationTokenEntity();
        entity.setUserId(USER_ID);
        entity.setToken(token);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setExpiresAt(expiresAt);
        return entity;
    }

    @Test
    void createEmailVerificationToken_persistsTokenWithFutureExpiry() {
        ArgumentCaptor<EmailVerificationTokenEntity> captor = ArgumentCaptor.forClass(EmailVerificationTokenEntity.class);
        when(repository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        String token = service.createEmailVerificationToken(USER_ID);

        assertThat(token).isNotBlank();
        assertThat(captor.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(captor.getValue().getExpiresAt()).isAfter(LocalDateTime.now());
    }

    @Test
    void resendEmailVerificationToken_noExistingToken_throwsInvalidTokenException() {
        when(repository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        assertThrows(InvalidTokenException.class, () -> service.resendEmailVerificationToken(USER_ID));
    }

    @Test
    void resendEmailVerificationToken_existingTokenStillValid_throwsInvalidSecurityOperation() {
        EmailVerificationTokenEntity stillValid = tokenEntity("t1", LocalDateTime.now().plusMinutes(2));
        when(repository.findByUserId(USER_ID)).thenReturn(Optional.of(stillValid));

        assertThrows(InvalidSecurityOperation.class, () -> service.resendEmailVerificationToken(USER_ID));

        verify(repository, never()).delete(any());
    }

    @Test
    void resendEmailVerificationToken_existingTokenExpired_deletesAndCreatesNewToken() {
        EmailVerificationTokenEntity expired = tokenEntity("t1", LocalDateTime.now().minusMinutes(1));
        when(repository.findByUserId(USER_ID)).thenReturn(Optional.of(expired));
        when(repository.save(any(EmailVerificationTokenEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        String newToken = service.resendEmailVerificationToken(USER_ID);

        verify(repository).delete(expired);
        assertThat(newToken).isNotBlank();
        assertThat(newToken).isNotEqualTo("t1");
    }

    @Test
    void verifyEmail_validToken_deletesTokenAndReturnsUserId() {
        EmailVerificationTokenEntity valid = tokenEntity("t1", LocalDateTime.now().plusMinutes(1));
        when(repository.findByToken("t1")).thenReturn(Optional.of(valid));

        Long userId = service.verifyEmail("t1");

        assertThat(userId).isEqualTo(USER_ID);
        verify(repository).delete(valid);
    }

    @Test
    void verifyEmail_tokenDoesNotExist_throwsInvalidTokenException() {
        when(repository.findByToken("missing")).thenReturn(Optional.empty());

        assertThrows(InvalidTokenException.class, () -> service.verifyEmail("missing"));
    }

    @Test
    void verifyEmail_tokenExpired_throwsInvalidTokenException() {
        EmailVerificationTokenEntity expired = tokenEntity("t1", LocalDateTime.now().minusMinutes(1));
        when(repository.findByToken("t1")).thenReturn(Optional.of(expired));

        assertThrows(InvalidTokenException.class, () -> service.verifyEmail("t1"));

        verify(repository, never()).delete(any());
    }
}
