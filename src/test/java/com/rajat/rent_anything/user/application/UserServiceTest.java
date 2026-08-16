package com.rajat.rent_anything.user.application;

import com.rajat.rent_anything.security.refreshTokens.RefreshTokenService;
import com.rajat.rent_anything.user.domain.User;
import com.rajat.rent_anything.user.enums.TrustStatus;
import com.rajat.rent_anything.user.enums.UserRole;
import com.rajat.rent_anything.user.exceptions.UserOperationException;
import com.rajat.rent_anything.user.infrastructure.UserEntity;
import com.rajat.rent_anything.user.infrastructure.UserRepository;
import com.rajat.rent_anything.user.records.response.UserProfileResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private BCryptPasswordEncoder passwordEncoder;

    @Mock
    private RefreshTokenService refreshTokenService;

    private UserService userService;

    private static final Long USER_ID = 1L;
    private static final String EMAIL = "user@example.com";

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, passwordEncoder, refreshTokenService);
    }

    private UserEntity userEntity(String hashedPassword) {
        UserEntity entity = new UserEntity();
        entity.setId(USER_ID);
        entity.setEmail(EMAIL);
        entity.setPassword(hashedPassword);
        entity.setRole(UserRole.USER);
        entity.setVerified(true);
        entity.setTrustStatus(TrustStatus.UNTRUSTED);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        return entity;
    }

    // ================= signUp =================

    @Test
    void signUp_newEmail_persistsHashedPasswordAndReturnsId() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(null);
        when(passwordEncoder.encode("rawPassword")).thenReturn("hashedPassword");
        when(userRepository.save(any(UserEntity.class))).thenAnswer(inv -> {
            UserEntity e = inv.getArgument(0);
            e.setId(USER_ID);
            return e;
        });

        Long result = userService.signUp(EMAIL, "rawPassword");

        assertThat(result).isEqualTo(USER_ID);

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPassword()).isEqualTo("hashedPassword");
        assertThat(captor.getValue().getRole()).isEqualTo(UserRole.USER);
        assertThat(captor.getValue().isVerified()).isFalse();
        assertThat(captor.getValue().getTrustStatus()).isEqualTo(TrustStatus.UNTRUSTED);
    }

    @Test
    void signUp_emailAlreadyRegistered_throwsUserOperationException() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(userEntity("existingHash"));

        assertThrows(UserOperationException.class, () -> userService.signUp(EMAIL, "rawPassword"));

        verify(userRepository, never()).save(any());
    }

    // ================= findByEmail =================

    @Test
    void findByEmail_userExists_returnsDomainUser() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(userEntity("hash"));

        User user = userService.findByEmail(EMAIL);

        assertThat(user.getEmail()).isEqualTo(EMAIL);
    }

    @Test
    void findByEmail_userDoesNotExist_throwsUserOperationException() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(null);

        assertThrows(UserOperationException.class, () -> userService.findByEmail(EMAIL));
    }

    // ================= getUserById / getUserCurrentProfile =================

    @Test
    void getUserById_userExists_returnsDomainUser() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(userEntity("hash")));

        User user = userService.getUserById(USER_ID);

        assertThat(user.getId()).isEqualTo(USER_ID);
    }

    @Test
    void getUserById_userDoesNotExist_throwsUserOperationException() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThrows(UserOperationException.class, () -> userService.getUserById(USER_ID));
    }

    @Test
    void getUserCurrentProfile_userExists_returnsProfileResponse() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(userEntity("hash")));

        UserProfileResponse profile = userService.getUserCurrentProfile(USER_ID);

        assertThat(profile.id()).isEqualTo(USER_ID);
        assertThat(profile.email()).isEqualTo(EMAIL);
    }

    // ================= changePassword =================

    @Test
    void changePassword_normalFlowWithCorrectCurrentPassword_updatesAndRevokesTokens() {
        UserEntity entity = userEntity("oldHash");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(entity));
        when(passwordEncoder.matches("oldRaw", "oldHash")).thenReturn(true);
        when(passwordEncoder.encode("newRaw")).thenReturn("newHash");

        userService.changePassword(USER_ID, "oldRaw", "newRaw", false);

        assertThat(entity.getPassword()).isEqualTo("newHash");
        verify(refreshTokenService).revokeUserTokens(USER_ID);
    }

    @Test
    void changePassword_normalFlowWithWrongCurrentPassword_throwsUserOperationException() {
        UserEntity entity = userEntity("oldHash");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(entity));
        when(passwordEncoder.matches("wrongRaw", "oldHash")).thenReturn(false);

        assertThrows(UserOperationException.class,
                () -> userService.changePassword(USER_ID, "wrongRaw", "newRaw", false));

        verify(refreshTokenService, never()).revokeUserTokens(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void changePassword_forgotPasswordFlow_skipsCurrentPasswordCheck() {
        UserEntity entity = userEntity("oldHash");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(entity));
        when(passwordEncoder.encode("newRaw")).thenReturn("newHash");

        userService.changePassword(USER_ID, null, "newRaw", true);

        assertThat(entity.getPassword()).isEqualTo("newHash");
        verify(passwordEncoder, never()).matches(any(), any());
        verify(refreshTokenService).revokeUserTokens(USER_ID);
    }

    // ================= markUserAsEmailVerified =================

    @Test
    void markUserAsEmailVerified_notYetVerified_setsVerifiedTrue() {
        UserEntity entity = userEntity("hash");
        entity.setVerified(false);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(entity));

        userService.markUserAsEmailVerified(USER_ID);

        assertThat(entity.isVerified()).isTrue();
        verify(userRepository).save(entity);
    }

    @Test
    void markUserAsEmailVerified_alreadyVerified_doesNotSaveAgain() {
        UserEntity entity = userEntity("hash");
        entity.setVerified(true);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(entity));

        userService.markUserAsEmailVerified(USER_ID);

        verify(userRepository, never()).save(any());
    }

    // ================= isAdmin =================

    @Test
    void isAdmin_userHasAdminRole_returnsTrue() {
        UserEntity entity = userEntity("hash");
        entity.setRole(UserRole.ADMIN);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(entity));

        assertThat(userService.isAdmin(USER_ID)).isTrue();
    }

    @Test
    void isAdmin_userHasRegularRole_returnsFalse() {
        UserEntity entity = userEntity("hash");
        entity.setRole(UserRole.USER);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(entity));

        assertThat(userService.isAdmin(USER_ID)).isFalse();
    }
}
