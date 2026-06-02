package com.rajat.rent_anything.security.passwordReset;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for managing password reset tokens.
 *
 * Provides database operations required for the
 * password reset workflow, including token lookup,
 * retrieval by user, and token cleanup.
 */
@Repository
public interface PasswordResetTokenRepository
          extends JpaRepository<PasswordResetTokenEntity, Long> {

     /**
      * Retrieves a password reset token by its token value.
      *
      * Used when validating password reset requests.
      *
      * @param token password reset token
      * @return matching token if found
      */
     Optional<PasswordResetTokenEntity> findByToken(String token);

     /**
      * Retrieves the password reset token associated with a user.
      *
      * Used when checking for existing reset requests
      * or replacing a previously issued token.
      *
      * @param userId user identifier
      * @return password reset token associated with the user
      */
     PasswordResetTokenEntity findByUserId(Long userId);

     /**
      * Deletes a password reset token.
      *
      * Typically used after a successful password reset
      * or when invalidating an existing token.
      *
      * @param entity token entity to delete
      */
     void delete(PasswordResetTokenEntity entity);

     /**
      * Deletes all password reset tokens associated with a user.
      *
      * Useful for ensuring that only one active reset token
      * exists per user at any given time.
      *
      * @param userId user identifier
      */
     void deleteByUserId(Long userId);
}