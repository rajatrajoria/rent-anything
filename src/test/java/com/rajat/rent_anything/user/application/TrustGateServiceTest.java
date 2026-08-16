package com.rajat.rent_anything.user.application;

import com.rajat.rent_anything.user.domain.User;
import com.rajat.rent_anything.user.enums.TrustStatus;
import com.rajat.rent_anything.user.exceptions.UserTrustGateFailureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrustGateServiceTest {

    @Mock
    private UserService userService;

    private TrustGateService trustGateService;

    private static final Long USER_ID = 1L;

    @BeforeEach
    void setUp() {
        trustGateService = new TrustGateService(userService);
    }

    private User userWithTrustStatus(TrustStatus status) {
        User user = User.rehydrate(USER_ID, "Name", "user@example.com", "hash", "555", true,
                com.rajat.rent_anything.user.enums.UserRole.USER, null, null, status);
        return user;
    }

    @Test
    void ensureUserIsTrusted_trustedUser_doesNotThrow() {
        when(userService.getUserById(USER_ID)).thenReturn(userWithTrustStatus(TrustStatus.TRUSTED));

        trustGateService.ensureUserIsTrusted(USER_ID);
    }

    @ParameterizedTest
    @EnumSource(value = TrustStatus.class, names = {"UNTRUSTED", "PENDING"})
    void ensureUserIsTrusted_notTrustedUser_throwsUserTrustGateFailureException(TrustStatus status) {
        when(userService.getUserById(USER_ID)).thenReturn(userWithTrustStatus(status));

        assertThrows(UserTrustGateFailureException.class,
                () -> trustGateService.ensureUserIsTrusted(USER_ID));
    }
}
