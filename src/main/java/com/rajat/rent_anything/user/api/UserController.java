package com.rajat.rent_anything.user.api;

import com.rajat.rent_anything.common.model.ApiResponse;
import com.rajat.rent_anything.security.CustomUserDetails;
import com.rajat.rent_anything.security.refreshTokens.RefreshTokenService;
import com.rajat.rent_anything.user.application.UserService;
import com.rajat.rent_anything.user.records.request.UpdatePasswordRequest;
import com.rajat.rent_anything.user.records.response.UserProfileResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/users")
public class UserController {
    private final UserService userService;
    private final RefreshTokenService refreshTokenService;

    public UserController(UserService userService, RefreshTokenService refreshTokenService) {
        this.userService = userService;
        this.refreshTokenService = refreshTokenService;
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getMyProfile(@AuthenticationPrincipal CustomUserDetails userDetails) {
        Long userId = userDetails.getDomainUser().getId();
        log.info("Fetching profile for user with ID: {}", userId);
        UserProfileResponse userProfileResponse = userService.getUserCurrentProfile(userId);
        return ResponseEntity.ok(ApiResponse.success(userProfileResponse));
    }

    @PutMapping("/password")
    public ResponseEntity<ApiResponse<Void>> updatePassword(@RequestBody UpdatePasswordRequest request, @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long userId = userDetails.getDomainUser().getId();
        log.info("User with ID: {} is attempting to change password", userId);
        userService.changePassword(userId, request.currentPassword(), request.newPassword(), false);
        log.info("Password change successful for user with ID: {}", userId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@RequestParam("refreshToken") String refreshToken, @AuthenticationPrincipal CustomUserDetails userDetail) {
        Long userId = userDetail.getDomainUser().getId();
        log.info("Logout attempt for userId: {}, refreshToken: {}", userId, refreshToken);
        refreshTokenService.revokeRefreshToken(refreshToken, userId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @PostMapping("/logoutAll")
    public ResponseEntity<ApiResponse<Void>> logoutAll(@AuthenticationPrincipal CustomUserDetails userDetails) {
        Long userId = userDetails.getDomainUser().getId();
        log.info("Logout all attempt for userId: {}", userId);
        refreshTokenService.revokeUserTokens(userId);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
