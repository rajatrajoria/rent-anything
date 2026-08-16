package com.rajat.rent_anything.security.passwordReset;

import com.rajat.rent_anything.common.exceptions.InvalidTokenException;
import com.rajat.rent_anything.user.application.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock
    private PasswordResetTokenRepository repository;

    @Mock
    private UserService userService;

    private PasswordResetService service;

    private static final Long USER_ID = 1L;
    private static final long EXPIRY_MINUTES = 15;

    @BeforeEach
    void setUp() {
        service = new PasswordResetService(repository, userService, EXPIRY_MINUTES);
    }

    private PasswordResetTokenEntity tokenEntity(String token, LocalDateTime expiryDate) {
        PasswordResetTokenEntity entity = new PasswordResetTokenEntity();
        entity.setUserId(USER_ID);
        entity.setToken(token);
        entity.setExpiryDate(expiryDate);
        entity.setCreatedAt(LocalDateTime.now());
        return entity;
    }

    @Test
    void createPasswordResetToken_deletesExistingTokenThenSavesNewOne() {
        ArgumentCaptor<PasswordResetTokenEntity> captor = ArgumentCaptor.forClass(PasswordResetTokenEntity.class);
        when(repository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        String token = service.createPasswordResetToken(USER_ID);

        InOrder order = inOrder(repository);
        order.verify(repository).deleteByUserId(USER_ID);
        order.verify(repository).save(any(PasswordResetTokenEntity.class));

        assertThat(token).isNotBlank();
        assertThat(captor.getValue().getExpiryDate()).isAfter(LocalDateTime.now());
    }

    @Test
    void verifyAndResetPassword_validToken_changesPasswordWithoutCurrentPasswordAndDeletesToken() {
        PasswordResetTokenEntity valid = tokenEntity("t1", LocalDateTime.now().plusMinutes(5));
        when(repository.findByToken("t1")).thenReturn(Optional.of(valid));

        service.verifyPasswordResetTokenAndResetPassword("t1", "newPassword");

        verify(userService).changePassword(USER_ID, null, "newPassword", true);
        verify(repository).delete(valid);
    }

    @Test
    void verifyAndResetPassword_tokenDoesNotExist_throwsInvalidTokenException() {
        when(repository.findByToken("missing")).thenReturn(Optional.empty());

        assertThrows(InvalidTokenException.class,
                () -> service.verifyPasswordResetTokenAndResetPassword("missing", "newPassword"));

        verify(userService, never()).changePassword(any(), any(), any(), anyBoolean());
    }

    @Test
    void verifyAndResetPassword_tokenExpired_throwsInvalidTokenException() {
        PasswordResetTokenEntity expired = tokenEntity("t1", LocalDateTime.now().minusMinutes(1));
        when(repository.findByToken("t1")).thenReturn(Optional.of(expired));

        assertThrows(InvalidTokenException.class,
                () -> service.verifyPasswordResetTokenAndResetPassword("t1", "newPassword"));

        verify(userService, never()).changePassword(any(), any(), any(), anyBoolean());
        verify(repository, never()).delete(any());
    }
}
