package com.rajat.rent_anything.user.application;

import com.rajat.rent_anything.user.enums.TrustStatus;
import com.rajat.rent_anything.user.enums.UserRole;
import com.rajat.rent_anything.user.exceptions.UserInputException;
import com.rajat.rent_anything.user.exceptions.UserOperationException;
import com.rajat.rent_anything.user.infrastructure.UserEntity;
import com.rajat.rent_anything.user.infrastructure.UserRepository;
import com.rajat.rent_anything.user.records.response.AdminUserListResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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

    @Test
    void listUsers_callerIsAdmin_returnsMappedPage() {
        when(userService.isAdmin(ADMIN_ID)).thenReturn(true);

        UserEntity pending = new UserEntity();
        pending.setId(TARGET_USER_ID);
        pending.setEmail("renter@example.com");
        pending.setName("Renter");
        pending.setVerified(true);
        pending.setTrustStatus(TrustStatus.PENDING);
        pending.setRole(UserRole.USER);
        pending.setCreatedAt(LocalDateTime.now());

        Pageable pageable = PageRequest.of(0, 20);
        when(userRepository.search(eq(TrustStatus.PENDING), isNull(), any()))
                .thenReturn(new PageImpl<>(java.util.List.of(pending), pageable, 1));

        AdminUserListResponse response = adminService.listUsers(ADMIN_ID, TrustStatus.PENDING, null, 0, 20);

        assertThat(response.users()).hasSize(1);
        assertThat(response.users().get(0).id()).isEqualTo(TARGET_USER_ID);
        assertThat(response.users().get(0).trustStatus()).isEqualTo(TrustStatus.PENDING);
        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.page()).isEqualTo(0);
    }

    @Test
    void listUsers_callerIsNotAdmin_throwsUserOperationException() {
        when(userService.isAdmin(ADMIN_ID)).thenReturn(false);

        assertThrows(UserOperationException.class,
                () -> adminService.listUsers(ADMIN_ID, null, null, 0, 20));

        verify(userRepository, never()).search(any(), any(), any());
    }
}
