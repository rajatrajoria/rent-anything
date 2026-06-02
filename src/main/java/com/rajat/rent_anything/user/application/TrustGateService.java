package com.rajat.rent_anything.user.application;

import com.rajat.rent_anything.common.enums.ErrorCode;
import com.rajat.rent_anything.user.domain.User;
import com.rajat.rent_anything.user.enums.TrustStatus;
import com.rajat.rent_anything.user.exceptions.UserTrustGateFailureException;
import org.springframework.stereotype.Service;

/**
 * Service responsible for enforcing trust-based access restrictions.
 *
 * Certain platform operations may only be performed by users who have
 * been reviewed and approved by administrators.
 *
 * This service acts as a centralized gatekeeper for such operations,
 * ensuring that trust-related business rules are applied consistently
 * throughout the application.
 */
@Service
public class TrustGateService {

    /**
     * Service used to retrieve user information.
     */
    private final UserService userService;

    public TrustGateService(UserService userService) {
        this.userService = userService;
    }

    /**
     * Verifies that a user has TRUSTED status.
     *
     * This method should be called before allowing access to
     * operations that require administrator-approved users.
     *
     * Business Rule:
     * Only users with TrustStatus.TRUSTED are allowed to
     * perform trust-gated actions.
     *
     * @param userId user attempting the operation
     * @throws UserTrustGateFailureException when the user is not trusted
     */
    public void ensureUserIsTrusted(Long userId) {

        User user = userService.getUserById(userId);

        if (user.getTrustStatus() != TrustStatus.TRUSTED) {
            throw new UserTrustGateFailureException(
                    ErrorCode.TRUST_GATE_FAILURE,
                    "User is not approved yet for transactions. Current trust status: "
                            + user.getTrustStatus()
            );
        }
    }
}
