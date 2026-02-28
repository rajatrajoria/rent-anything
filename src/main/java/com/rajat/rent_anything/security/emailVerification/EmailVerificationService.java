package com.rajat.rent_anything.security.emailVerification;

import com.rajat.rent_anything.common.enums.ErrorCode;
import com.rajat.rent_anything.common.exceptions.InvalidSecurityOperation;
import com.rajat.rent_anything.common.exceptions.InvalidTokenException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
public class EmailVerificationService {
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final long expiryMinutes;

    public EmailVerificationService(
            @Value("${jwt.email.verification.expiration-minutes}")
            long expiryMinutes,
            EmailVerificationTokenRepository emailVerificationTokenRepository) {
        this.emailVerificationTokenRepository = emailVerificationTokenRepository;
        this.expiryMinutes = expiryMinutes;
    }

    @Transactional
    public String createEmailVerificationToken(Long userId) {
        EmailVerificationTokenEntity entity = new EmailVerificationTokenEntity();
        entity.setUserId(userId);
        entity.setToken(UUID.randomUUID().toString());
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        entity.setExpiresAt(LocalDateTime.now().plusMinutes(expiryMinutes));
        emailVerificationTokenRepository.save(entity);
        return entity.getToken();
    }

    @Transactional
    public String resendEmailVerificationToken(Long userId) {
        EmailVerificationTokenEntity entity = emailVerificationTokenRepository.findByUserId(userId)
                .orElseThrow(() -> new InvalidTokenException(ErrorCode.INVALID_TOKEN, "No existing verification token found for user ID: " + userId));
        if(entity.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw new InvalidSecurityOperation(ErrorCode.INVALID_SECURITY_OPERATION, "Existing verification token is still valid. Please wait before requesting a new one.");
        }
        emailVerificationTokenRepository.delete(entity);
        return createEmailVerificationToken(userId);
    }

    @Transactional
    public Long verifyEmail(String token) {
        log.info("Attempting to verify email with token: {}", token);
        EmailVerificationTokenEntity entity = emailVerificationTokenRepository.findByToken(token)
                .orElseThrow(() -> new InvalidTokenException(ErrorCode.INVALID_TOKEN, "Invalid verification token"));
        if(entity.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new InvalidTokenException(ErrorCode.INVALID_TOKEN, "Verification token has expired");
        }
        emailVerificationTokenRepository.delete(entity);
        log.info("Email verification successful for user with ID: {}", entity.getUserId());
        return entity.getUserId();
    }


}
