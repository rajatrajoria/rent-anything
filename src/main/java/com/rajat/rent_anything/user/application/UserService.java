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

@Slf4j
@Service
public class UserService {
    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;

    public UserService(UserRepository userRepository, BCryptPasswordEncoder passwordEncoder, RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
    }

    public Long signUp(String email, String rawPassword) {
        if (userRepository.findByEmail(email) != null) {
            throw new UserOperationException(ErrorCode.EMAIL_ALREADY_IN_USE, "Email already in use");
        }
        String hashedPassword = passwordEncoder.encode(rawPassword);
        User newUser = User.createUserAtSignUp(email, hashedPassword);
        UserEntity newUserEntity = UserMapper.toEntity(newUser);
        UserEntity newUserSaved = userRepository.save(newUserEntity);
        log.info("New user successfully signed up with email: {}", email);
        return newUserSaved.getId();
    }

    public User findByEmail(String email) {
        UserEntity userEntity = userRepository.findByEmail(email);
        if (userEntity == null) {
            throw new UserOperationException(ErrorCode.USER_NOT_FOUND, "User not found with email: " + email);
        }
        return UserMapper.toDomain(userEntity);
    }

    public User getUserById(Long id) {
        UserEntity userEntity = getUserEntityById(id);
        return UserMapper.toDomain(userEntity);
    }

    public UserProfileResponse getUserCurrentProfile(Long userId) {
        UserEntity userEntity = userRepository.findById(userId)
                .orElseThrow(() -> new UserOperationException(ErrorCode.USER_NOT_FOUND, "User not found with id: " + userId));
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

    @Transactional
    public void changePassword(Long userId, String currentRawPassword, String newRawPassword, boolean isForgotPasswordFlow) {
        UserEntity userEntity = getUserEntityById(userId);

        if (!isForgotPasswordFlow && !passwordEncoder.matches(currentRawPassword, userEntity.getPassword())) {
            throw new UserOperationException(ErrorCode.INVALID_PASSWORD, "Current password is incorrect");
        }

        String newHashedPassword = passwordEncoder.encode(newRawPassword);
        userEntity.setPassword(newHashedPassword);
        userEntity.setUpdatedAt(LocalDateTime.now());

        userRepository.save(userEntity);
        refreshTokenService.revokeUserTokens(userId);

        log.info("Password changed successfully for userId: {}. Older refresh tokens revoked for all devices and new ones added for current device", userId);

    }

    public void markUserAsEmailVerified(Long userId) {
        UserEntity userEntity = getUserEntityById(userId);
        if (userEntity.isVerified()) {
            log.info("User with ID: {} is already marked as email verified", userId);
            return;
        }
        userEntity.setVerified(true);
        userEntity.setUpdatedAt(LocalDateTime.now());
        userRepository.save(userEntity);
        log.info("User with ID: {} marked as email verified", userId);
    }

    private UserEntity getUserEntityById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserOperationException(ErrorCode.USER_NOT_FOUND, "User not found with id: " + userId));
    }

    public boolean isAdmin(Long userId) {
        UserEntity userEntity = getUserEntityById(userId);
        return userEntity.getRole() == UserRole.ADMIN;
    }
}
