package com.rajat.rent_anything.security.passwordReset;

import com.rajat.rent_anything.common.enums.ErrorCode;
import com.rajat.rent_anything.common.exceptions.InvalidTokenException;
import com.rajat.rent_anything.user.application.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class PasswordResetService {

    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final UserService userService;
    private final long expiryMinutes;

    public PasswordResetService(
            PasswordResetTokenRepository passwordResetTokenRepository, UserService userService,
            @Value("${jwt.password.reset.token.expiration-minutes:30}") long expiryMinutes
    ) {
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.userService = userService;
        this.expiryMinutes = expiryMinutes;
    }

    @Transactional
    public String createPasswordResetToken(Long userId) {
        passwordResetTokenRepository.deleteByUserId(userId);
        PasswordResetTokenEntity tokenEntity = new PasswordResetTokenEntity();
        tokenEntity.setUserId(userId);
        tokenEntity.setToken(UUID.randomUUID().toString());
        tokenEntity.setCreatedAt(LocalDateTime.now());
        tokenEntity.setExpiryDate(LocalDateTime.now().plusMinutes(expiryMinutes));
        passwordResetTokenRepository.save(tokenEntity);
        return tokenEntity.getToken();
    }

    @Transactional
    public void verifyPasswordResetTokenAndResetPassword(String token, String newPassword) {
        PasswordResetTokenEntity tokenEntity = passwordResetTokenRepository.findByToken(token)
                .orElseThrow(() -> new InvalidTokenException(ErrorCode.INVALID_TOKEN, "Invalid password reset token"));
        if (tokenEntity.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new InvalidTokenException(ErrorCode.INVALID_TOKEN, "Password reset token has expired");
        }
        userService.changePassword(tokenEntity.getUserId(), null, newPassword, true);
        passwordResetTokenRepository.delete(tokenEntity);
    }

}
