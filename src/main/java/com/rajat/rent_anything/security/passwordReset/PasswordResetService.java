package com.rajat.rent_anything.security.passwordReset;

import com.rajat.rent_anything.common.enums.ErrorCode;
import com.rajat.rent_anything.common.exceptions.InvalidTokenException;
import com.rajat.rent_anything.user.application.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Service responsible for managing password reset tokens
 * and handling password reset requests.
 *
 * Business Rules:
 * - Password reset tokens are temporary and expire after a configurable duration.
 * - Only one active reset token is allowed per user at a time.
 * - Tokens are deleted after successful password reset.
 */
@Service
public class PasswordResetService {

    /**
     * Repository used for storing and retrieving password reset tokens.
     */
    private final PasswordResetTokenRepository passwordResetTokenRepository;

    /**
     * Service used to update the user's password after
     * successful token validation.
     */
    private final UserService userService;

    /**
     * Password reset token validity duration in minutes.
     */
    private final long expiryMinutes;

    public PasswordResetService(
            PasswordResetTokenRepository passwordResetTokenRepository,
            UserService userService,
            @Value("${jwt.password.reset.token.expiration-minutes:30}") long expiryMinutes
    ) {
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.userService = userService;
        this.expiryMinutes = expiryMinutes;
    }

    /**
     * Creates a new password reset token for a user.
     *
     * Before creating a new token, any existing token
     * associated with the user is removed to ensure
     * only one active reset token exists.
     *
     * @param userId user requesting password reset
     * @return generated password reset token
     */
    @Transactional
    public String createPasswordResetToken(Long userId) {

        // Remove any previously issued reset token for this user.
        passwordResetTokenRepository.deleteByUserId(userId);

        PasswordResetTokenEntity tokenEntity = new PasswordResetTokenEntity();

        tokenEntity.setUserId(userId);

        // Generate a unique token that will be sent via email.
        tokenEntity.setToken(UUID.randomUUID().toString());

        tokenEntity.setCreatedAt(LocalDateTime.now());

        // Token remains valid for the configured duration.
        tokenEntity.setExpiryDate(
                LocalDateTime.now().plusMinutes(expiryMinutes)
        );

        passwordResetTokenRepository.save(tokenEntity);

        return tokenEntity.getToken();
    }

    /**
     * Validates a password reset token and updates
     * the user's password.
     *
     * Validation performed:
     * - Token must exist.
     * - Token must not be expired.
     *
     * After a successful password reset, the token
     * is deleted to prevent reuse.
     *
     * @param token password reset token
     * @param newPassword new password provided by the user
     * @throws InvalidTokenException if the token is invalid or expired
     */
    @Transactional
    public void verifyPasswordResetTokenAndResetPassword(
            String token,
            String newPassword
    ) {

        PasswordResetTokenEntity tokenEntity =
                passwordResetTokenRepository.findByToken(token)
                        .orElseThrow(() ->
                                new InvalidTokenException(
                                        ErrorCode.INVALID_TOKEN,
                                        "Invalid password reset token"
                                )
                        );

        if (tokenEntity.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new InvalidTokenException(
                    ErrorCode.INVALID_TOKEN,
                    "Password reset token has expired"
            );
        }

        // Reset password without requiring the current password,
        // since ownership has already been verified through the token.
        userService.changePassword(
                tokenEntity.getUserId(),
                null,
                newPassword,
                true
        );

        // Invalidate token after successful use.
        passwordResetTokenRepository.delete(tokenEntity);
    }
}
