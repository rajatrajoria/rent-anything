package com.rajat.rent_anything.user.application;

import com.rajat.rent_anything.common.enums.ErrorCode;
import com.rajat.rent_anything.security.refreshTokens.RefreshTokenService;
import com.rajat.rent_anything.user.domain.User;
import com.rajat.rent_anything.user.enums.UserRole;
import com.rajat.rent_anything.user.exceptions.UserOperationException;
import com.rajat.rent_anything.user.infrastructure.UserEntity;
import com.rajat.rent_anything.user.infrastructure.UserMapper;
import com.rajat.rent_anything.user.infrastructure.UserRepository;
import com.rajat.rent_anything.user.records.response.UserProfileResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Core service responsible for user account management.
 *
 * Responsibilities:
 * - User registration
 * - User retrieval
 * - Profile retrieval
 * - Password management
 * - Email verification updates
 * - User role checks
 *
 * This service acts as the primary entry point for user-related
 * business operations and coordinates interactions between the
 * domain model and persistence layer.
 */
@Slf4j
@Service
public class UserService {

    /**
     * Repository used for user persistence operations.
     */
    private final UserRepository userRepository;

    /**
     * Password encoder used for password hashing and verification.
     *
     * Plain-text passwords should never be stored in the database.
     */
    private final BCryptPasswordEncoder passwordEncoder;

    /**
     * Service responsible for refresh token revocation.
     *
     * Used during security-sensitive operations such as password changes.
     */
    private final RefreshTokenService refreshTokenService;

    public UserService(
            UserRepository userRepository,
            BCryptPasswordEncoder passwordEncoder,
            RefreshTokenService refreshTokenService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
    }

    /**
     * Registers a new user account.
     *
     * Workflow:
     * 1. Ensure email is not already in use.
     * 2. Hash the provided password.
     * 3. Create domain user object.
     * 4. Persist user.
     * 5. Return generated user id.
     *
     * @param email user's email address
     * @param rawPassword user's plain-text password
     * @return newly created user id
     * @throws UserOperationException when the email is already registered
     */
    public Long signUp(String email, String rawPassword) {

        if (userRepository.findByEmail(email) != null) {
            throw new UserOperationException(
                    ErrorCode.EMAIL_ALREADY_IN_USE,
                    "Email already in use"
            );
        }

        String hashedPassword = passwordEncoder.encode(rawPassword);

        User newUser = User.createUserAtSignUp(
                email,
                hashedPassword
        );

        UserEntity newUserEntity = UserMapper.toEntity(newUser);

        UserEntity newUserSaved = userRepository.save(newUserEntity);

        log.info(
                "New user successfully signed up with email: {}",
                email
        );

        return newUserSaved.getId();
    }

    /**
     * Retrieves a user by email address.
     *
     * @param email user email
     * @return user domain object
     * @throws UserOperationException when the user cannot be found
     */
    public User findByEmail(String email) {

        UserEntity userEntity = userRepository.findByEmail(email);

        if (userEntity == null) {
            throw new UserOperationException(
                    ErrorCode.USER_NOT_FOUND,
                    "User not found with email: " + email
            );
        }

        return UserMapper.toDomain(userEntity);
    }

    /**
     * Retrieves a user by identifier.
     *
     * @param id user identifier
     * @return user domain object
     */
    public User getUserById(Long id) {
        UserEntity userEntity = getUserEntityById(id);
        return UserMapper.toDomain(userEntity);
    }

    /**
     * Retrieves the current profile information for a user.
     *
     * This method returns a response DTO specifically intended
     * for profile display purposes.
     *
     * @param userId user identifier
     * @return current user profile
     */
    public UserProfileResponse getUserCurrentProfile(Long userId) {

        UserEntity userEntity = userRepository.findById(userId)
                .orElseThrow(() ->
                        new UserOperationException(
                                ErrorCode.USER_NOT_FOUND,
                                "User not found with id: " + userId
                        )
                );

        return new UserProfileResponse(
                userEntity.getId(),
                userEntity.getEmail(),
                userEntity.getName(),
                userEntity.getMobileNumber(),
                userEntity.isVerified(),
                userEntity.getRole().name(),
                userEntity.getCreatedAt(),
                userEntity.getUpdatedAt()
        );
    }

    /**
     * Updates a user's password.
     *
     * Business Rules:
     * - Current password validation is required during normal password changes.
     * - Current password validation is skipped during forgot-password flows.
     * - All refresh tokens are revoked after a successful password change.
     *
     * Revoking refresh tokens ensures that existing authenticated sessions
     * cannot continue using previously issued credentials.
     *
     * @param userId target user
     * @param currentRawPassword current password supplied by user
     * @param newRawPassword new password to be set
     * @param isForgotPasswordFlow indicates whether password reset
     *                             originated from forgot-password workflow
     */
    @Transactional
    public void changePassword(
            Long userId,
            String currentRawPassword,
            String newRawPassword,
            boolean isForgotPasswordFlow
    ) {

        UserEntity userEntity = getUserEntityById(userId);

        if (!isForgotPasswordFlow
                && !passwordEncoder.matches(
                        currentRawPassword,
                        userEntity.getPassword()
                )) {

            throw new UserOperationException(
                    ErrorCode.INVALID_PASSWORD,
                    "Current password is incorrect"
            );
        }

        String newHashedPassword =
                passwordEncoder.encode(newRawPassword);

        userEntity.setPassword(newHashedPassword);
        userEntity.setUpdatedAt(LocalDateTime.now());

        userRepository.save(userEntity);

        // Invalidate all active sessions after password change.
        refreshTokenService.revokeUserTokens(userId);

        log.info(
                "Password changed successfully for userId: {}",
                userId
        );
    }

    /**
     * Marks a user's email as verified.
     *
     * This method is typically invoked after successful
     * email verification token validation.
     *
     * @param userId verified user identifier
     */
    public void markUserAsEmailVerified(Long userId) {

        UserEntity userEntity = getUserEntityById(userId);

        if (userEntity.isVerified()) {
            log.info(
                    "User with ID: {} is already marked as email verified",
                    userId
            );
            return;
        }

        userEntity.setVerified(true);
        userEntity.setUpdatedAt(LocalDateTime.now());

        userRepository.save(userEntity);

        log.info(
                "User with ID: {} marked as email verified",
                userId
        );
    }

    /**
     * Retrieves a user entity from the database.
     *
     * Centralizing this lookup avoids duplication of
     * user-not-found validation logic.
     *
     * @param userId user identifier
     * @return persisted user entity
     * @throws UserOperationException when the user cannot be found
     */
    private UserEntity getUserEntityById(Long userId) {

        return userRepository.findById(userId)
                .orElseThrow(() ->
                        new UserOperationException(
                                ErrorCode.USER_NOT_FOUND,
                                "User not found with id: " + userId
                        )
                );
    }

    /**
     * Determines whether a user has administrator privileges.
     *
     * @param userId user identifier
     * @return true if the user has ADMIN role
     */
    public boolean isAdmin(Long userId) {

        UserEntity userEntity = getUserEntityById(userId);

        return userEntity.getRole() == UserRole.ADMIN;
    }
}