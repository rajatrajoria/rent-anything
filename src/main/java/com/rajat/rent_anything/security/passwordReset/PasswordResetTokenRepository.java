package com.rajat.rent_anything.security.passwordReset;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetTokenEntity, Long> {
     Optional<PasswordResetTokenEntity> findByToken(String token);
     PasswordResetTokenEntity findByUserId(Long userId);
     void delete(PasswordResetTokenEntity entity);
     void deleteByUserId(Long userId);
}
