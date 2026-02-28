package com.rajat.rent_anything.user.domain;

import com.rajat.rent_anything.common.enums.ErrorCode;
import com.rajat.rent_anything.user.enums.TrustStatus;
import com.rajat.rent_anything.user.enums.UserRole;
import com.rajat.rent_anything.user.exceptions.UserInputException;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class User {

    private Long id;
    private String email;
    private String password;
    private UserRole role;
    private String name;
    private String mobileNumber;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private boolean isVerified;
    private TrustStatus trustStatus;

    private User() {
    }

    public static User createUserAtSignUp(String email, String encodedPassword) {
        if (email == null || email.isEmpty()) {
            throw new UserInputException(ErrorCode.INVALID_USER_INPUT, "Email cannot be empty");
        }
        if (encodedPassword == null || encodedPassword.isEmpty()) {
            throw new UserInputException(ErrorCode.INVALID_USER_INPUT, "Password cannot be empty");
        }

        User user = new User();
        user.email = email;
        user.password = encodedPassword;
        user.isVerified = false;
        user.role = UserRole.USER;
        user.createdAt = LocalDateTime.now();
        user.updatedAt = LocalDateTime.now();
        user.trustStatus = TrustStatus.UNTRUSTED;
        return user;
    }

    public static User create(
            String name,
            String email,
            String password,
            String mobileNumber,
            UserRole role
    ) {
        if (name == null || name.isEmpty()) {
            throw new UserInputException(ErrorCode.INVALID_USER_INPUT, "Name cannot be empty");
        }
        if (email == null || email.isEmpty()) {
            throw new UserInputException(ErrorCode.INVALID_USER_INPUT, "Email cannot be empty");
        }
        if (password == null || password.isEmpty()) {
            throw new UserInputException(ErrorCode.INVALID_USER_INPUT, "Password cannot be empty");
        }
        if (mobileNumber == null || mobileNumber.isEmpty()) {
            throw new UserInputException(ErrorCode.INVALID_USER_INPUT, "Mobile number cannot be empty");
        }
        if (role == null) {
            throw new UserInputException(ErrorCode.INVALID_USER_INPUT, "Role cannot be empty");
        }

        User user = new User();
        user.name = name;
        user.email = email;
        user.password = password;
        user.mobileNumber = mobileNumber;
        user.role = role;
        user.isVerified = false; // default to false when creating a new user
        user.createdAt = LocalDateTime.now();
        user.updatedAt = LocalDateTime.now();
        user.trustStatus = TrustStatus.UNTRUSTED; // default to UNTRUSTED when creating a new user
        return user;
    }

    public static User rehydrate(
            Long id,
            String name,
            String email,
            String password,
            String mobileNumber,
            boolean isVerified,
            UserRole role,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            TrustStatus trustStatus
    ) {
        User user = new User();
        user.id = id;
        user.name = name;
        user.email = email;
        user.password = password;
        user.mobileNumber = mobileNumber;
        user.isVerified = isVerified;
        user.role = role;
        user.createdAt = createdAt;
        user.updatedAt = updatedAt;
        user.trustStatus = trustStatus;
        return user;
    }
}
