package com.rajat.rent_anything.security.emailVerification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationTokenEntity, Long> {
    Optional<EmailVerificationTokenEntity> findByToken(String token);
    Optional<EmailVerificationTokenEntity> findByUserId(Long userId);
    void delete(EmailVerificationTokenEntity entity);
}
