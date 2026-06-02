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

/**
 * REST controller exposing authenticated user operations.
 *
 * All endpoints in this controller require a valid authenticated user.
 *
 * Responsibilities:
 * - Fetch current user's profile
 * - Update password
 * - Logout current session
 * - Logout all active sessions
 *
 * The currently authenticated user is obtained from Spring Security's
 * SecurityContext through the @AuthenticationPrincipal annotation.
 */
@Slf4j
@RestController
@RequestMapping("/users")
public class UserController {

    /**
     * Service responsible for user profile and account operations.
     */
    private final UserService userService;

    /**
     * Service responsible for refresh token management and revocation.
     */
    private final RefreshTokenService refreshTokenService;

    public UserController(
            UserService userService,
            RefreshTokenService refreshTokenService) {
        this.userService = userService;
        this.refreshTokenService = refreshTokenService;
    }

    /**
     * Retrieves the profile of the currently authenticated user.
     *
     * The authenticated user's identity is extracted from the
     * SecurityContext and used to fetch the latest profile data.
     *
     * @param userDetails authenticated user details
     * @return current user's profile information
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getMyProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Long userId = userDetails.getDomainUser().getId();

        log.info("Fetching profile for user with ID: {}", userId);

        UserProfileResponse userProfileResponse = userService.getUserCurrentProfile(userId);

        return ResponseEntity.ok(
                ApiResponse.success(userProfileResponse));
    }

    /**
     * Updates the password of the currently authenticated user.
     *
     * Security Rules:
     * - User must provide their current password.
     * - Current password is validated before updating.
     * - New password replaces the existing password.
     *
     * This endpoint is intended for authenticated users who
     * wish to change their password from within the application.
     *
     * @param request     password change request
     * @param userDetails authenticated user details
     * @return success response
     */
    @PutMapping("/password")
    public ResponseEntity<ApiResponse<Void>> updatePassword(
            @RequestBody UpdatePasswordRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Long userId = userDetails.getDomainUser().getId();

        log.info(
                "User with ID: {} is attempting to change password",
                userId);

        userService.changePassword(
                userId,
                request.currentPassword(),
                request.newPassword(),
                false);

        log.info(
                "Password change successful for user with ID: {}",
                userId);

        return ResponseEntity.ok(ApiResponse.success());
    }

    /**
     * Logs the user out from the current session.
     *
     * The supplied refresh token is revoked, preventing it
     * from being used to obtain new access tokens.
     *
     * Access tokens remain valid until their expiration time,
     * while refresh token revocation prevents future session renewal.
     *
     * @param refreshToken refresh token associated with the session
     * @param userDetail   authenticated user details
     * @return success response
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestParam("refreshToken") String refreshToken,
            @AuthenticationPrincipal CustomUserDetails userDetail) {

        Long userId = userDetail.getDomainUser().getId();

        log.info(
                "Logout attempt for userId: {}, refreshToken: {}",
                userId,
                refreshToken);

        refreshTokenService.revokeRefreshToken(
                refreshToken,
                userId);

        return ResponseEntity.ok(ApiResponse.success());
    }

    /**
     * Logs the user out from all active sessions.
     *
     * All refresh tokens belonging to the user are revoked,
     * effectively invalidating every active login session.
     *
     * Common Use Cases:
     * - Password change
     * - Suspicious account activity
     * - User requests logout from all devices
     *
     * @param userDetails authenticated user details
     * @return success response
     */
    @PostMapping("/logoutAll")
    public ResponseEntity<ApiResponse<Void>> logoutAll(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Long userId = userDetails.getDomainUser().getId();

        log.info(
                "Logout all attempt for userId: {}",
                userId);

        refreshTokenService.revokeUserTokens(userId);

        return ResponseEntity.ok(ApiResponse.success());
    }
}
