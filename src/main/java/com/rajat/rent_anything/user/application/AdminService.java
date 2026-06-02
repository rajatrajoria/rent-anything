package com.rajat.rent_anything.user.application;

import com.rajat.rent_anything.user.enums.TrustStatus;
import com.rajat.rent_anything.user.exceptions.UserInputException;
import com.rajat.rent_anything.user.exceptions.UserOperationException;
import com.rajat.rent_anything.user.infrastructure.UserEntity;
import com.rajat.rent_anything.user.infrastructure.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static com.rajat.rent_anything.common.enums.ErrorCode.*;

/**
 * Service responsible for administrative user-management operations.
 *
 * Responsibilities:
 * - Manage user trust status
 * - Perform admin-only user actions
 *
 * Trust status is a platform-level moderation mechanism that allows
 * administrators to mark users with different levels of trust based
 * on platform policies and user behavior.
 */
@Service
public class AdminService {

    /**
     * Repository used for user persistence operations.
     */
    private final UserRepository userRepository;

    /**
     * Service used for user-related validations and lookups.
     */
    private final UserService userService;

    public AdminService(
            UserRepository userRepository,
            UserService userService
    ) {
        this.userRepository = userRepository;
        this.userService = userService;
    }

    /**
     * Updates the trust status of a user.
     *
     * Business Rules:
     * - Only administrators can perform this action.
     * - Administrators cannot modify their own trust status.
     * - The target user must exist.
     *
     * Trust status can be used by the platform to determine
     * whether a user is considered trusted, restricted,
     * or requires additional review.
     *
     * @param adminId administrator performing the action
     * @param userId target user identifier
     * @param newStatus new trust status to assign
     * @throws UserOperationException when the caller is not an administrator
     *                                or the target user cannot be found
     * @throws UserInputException when an administrator attempts to modify
     *                            their own trust status
     */
    @Transactional
    public void updateUserTrustStatus(
            Long adminId,
            Long userId,
            TrustStatus newStatus
    ) {

        // Only administrators are allowed to modify trust status.
        if (!userService.isAdmin(adminId)) {
            throw new UserOperationException(
                    USER_OPERATION_UNAUTHORIZED,
                    "Only admins can update trust status"
            );
        }

        // Prevent administrators from modifying their own trust status.
        if (adminId.equals(userId)) {
            throw new UserInputException(
                    INVALID_USER_INPUT,
                    "Admin cannot modify own status"
            );
        }

        UserEntity userEntity = userRepository.findById(userId)
                .orElseThrow(() ->
                        new UserOperationException(
                                USER_NOT_FOUND,
                                "User not found"
                        )
                );

        userEntity.setTrustStatus(newStatus);

        // Update audit timestamp to reflect the administrative change.
        userEntity.setUpdatedAt(LocalDateTime.now());
    }
}