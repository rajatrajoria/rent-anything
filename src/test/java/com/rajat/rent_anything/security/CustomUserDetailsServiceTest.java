package com.rajat.rent_anything.security;

import com.rajat.rent_anything.common.enums.ErrorCode;
import com.rajat.rent_anything.user.application.UserService;
import com.rajat.rent_anything.user.domain.User;
import com.rajat.rent_anything.user.enums.TrustStatus;
import com.rajat.rent_anything.user.enums.UserRole;
import com.rajat.rent_anything.user.exceptions.UserOperationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UserService userService;

    private CustomUserDetailsService customUserDetailsService;

    private static final String EMAIL = "user@example.com";

    @BeforeEach
    void setUp() {
        customUserDetailsService = new CustomUserDetailsService(userService);
    }

    @Test
    void loadUserByUsername_userExists_returnsCustomUserDetailsWrappingDomainUser() {
        User user = User.rehydrate(1L, "Name", EMAIL, "hash", "555", true,
                UserRole.USER, null, null, TrustStatus.TRUSTED);
        when(userService.findByEmail(EMAIL)).thenReturn(user);

        CustomUserDetails result = customUserDetailsService.loadUserByUsername(EMAIL);

        assertThat(result.getUsername()).isEqualTo(EMAIL);
        assertThat(result.getDomainUser()).isSameAs(user);
    }

    @Test
    void loadUserByUsername_userDoesNotExist_throwsUsernameNotFoundException() {
        when(userService.findByEmail(EMAIL))
                .thenThrow(new UserOperationException(ErrorCode.USER_NOT_FOUND, "User not found with email: " + EMAIL));

        assertThrows(UsernameNotFoundException.class, () -> customUserDetailsService.loadUserByUsername(EMAIL));
    }
}
