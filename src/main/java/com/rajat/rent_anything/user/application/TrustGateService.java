package com.rajat.rent_anything.user.application;

import com.rajat.rent_anything.common.enums.ErrorCode;
import com.rajat.rent_anything.user.domain.User;
import com.rajat.rent_anything.user.enums.TrustStatus;
import com.rajat.rent_anything.user.exceptions.UserOperationException;
import com.rajat.rent_anything.user.exceptions.UserTrustGateFailureException;
import org.springframework.stereotype.Service;

import java.nio.file.AccessDeniedException;

@Service
public class TrustGateService {
    private final UserService userService;

    public TrustGateService(UserService userService) {
        this.userService = userService;
    }

    public void ensureUserIsTrusted(Long userId) {
        User user = userService.getUserById(userId);
        if (user.getTrustStatus() != TrustStatus.TRUSTED) {
            throw new UserTrustGateFailureException(ErrorCode.TRUST_GATE_FAILURE, "User is not approved yet for transactions. Current trust status: " + user.getTrustStatus());
        }
    }
}
