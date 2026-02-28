package com.rajat.rent_anything.user.application;

import com.rajat.rent_anything.user.domain.User;
import com.rajat.rent_anything.user.enums.TrustStatus;
import com.rajat.rent_anything.user.exceptions.UserInputException;
import com.rajat.rent_anything.user.exceptions.UserOperationException;
import com.rajat.rent_anything.user.infrastructure.UserEntity;
import com.rajat.rent_anything.user.infrastructure.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static com.rajat.rent_anything.common.enums.ErrorCode.*;

@Service
public class AdminService {

    private final UserRepository userRepository;
    private final UserService userService;

    public AdminService(UserRepository userRepository,
                        UserService userService) {
        this.userRepository = userRepository;
        this.userService = userService;
    }

    @Transactional
    public void updateUserTrustStatus(Long adminId, Long userId, TrustStatus newStatus) {
        if (!userService.isAdmin(adminId)) {
            throw new UserOperationException(USER_OPERATION_UNAUTHORIZED, "Only admins can update trust status");
        }
        if (adminId.equals(userId)) {
            throw new UserInputException(INVALID_USER_INPUT, "Admin cannot modify own status");
        }
        UserEntity userEntity = userRepository.findById(userId).orElseThrow(() -> new UserOperationException(
                        USER_NOT_FOUND,
                        "User not found"
        ));
        userEntity.setTrustStatus(newStatus);
        userEntity.setUpdatedAt(LocalDateTime.now());
    }
}
