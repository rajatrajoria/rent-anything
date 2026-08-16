package com.rajat.rent_anything.user.application;

import com.rajat.rent_anything.user.enums.TrustStatus;
import com.rajat.rent_anything.user.exceptions.UserInputException;
import com.rajat.rent_anything.user.exceptions.UserOperationException;
import com.rajat.rent_anything.user.infrastructure.UserEntity;
import com.rajat.rent_anything.user.infrastructure.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserService userService;

    private AdminService adminService;

    private static final Long ADMIN_ID = 1L;
    private static final Long TARGET_USER_ID = 2L;

    @BeforeEach
    void setUp() {
        adminService = new AdminService(userRepository, userService);
    }

    @Test
    void updateUserTrustStatus_callerIsAdmin_updatesTargetUser() {
        when(userService.isAdmin(ADMIN_ID)).thenReturn(true);
        UserEntity target = new UserEntity();
        target.setId(TARGET_USER_ID);
        target.setTrustStatus(TrustStatus.UNTRUSTED);
        when(userRepository.findById(TARGET_USER_ID)).thenReturn(Optional.of(target));

        adminService.updateUserTrustStatus(ADMIN_ID, TARGET_USER_ID, TrustStatus.TRUSTED);

        assertThat(target.getTrustStatus()).isEqualTo(TrustStatus.TRUSTED);
    }

    @Test
    void updateUserTrustStatus_callerIsNotAdmin_throwsUserOperationException() {
        when(userService.isAdmin(ADMIN_ID)).thenReturn(false);

        assertThrows(UserOperationException.class,
                () -> adminService.updateUserTrustStatus(ADMIN_ID, TARGET_USER_ID, TrustStatus.TRUSTED));

        verify(userRepository, never()).findById(any());
    }

    @Test
    void updateUserTrustStatus_adminTargetsOwnAccount_throwsUserInputException() {
        when(userService.isAdmin(ADMIN_ID)).thenReturn(true);

        assertThrows(UserInputException.class,
                () -> adminService.updateUserTrustStatus(ADMIN_ID, ADMIN_ID, TrustStatus.TRUSTED));

        verify(userRepository, never()).findById(any());
    }

    @Test
    void updateUserTrustStatus_targetUserDoesNotExist_throwsUserOperationException() {
        when(userService.isAdmin(ADMIN_ID)).thenReturn(true);
        when(userRepository.findById(TARGET_USER_ID)).thenReturn(Optional.empty());

        assertThrows(UserOperationException.class,
                () -> adminService.updateUserTrustStatus(ADMIN_ID, TARGET_USER_ID, TrustStatus.TRUSTED));
    }
}
