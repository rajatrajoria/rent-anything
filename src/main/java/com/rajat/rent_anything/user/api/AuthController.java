package com.rajat.rent_anything.user.api;

import com.rajat.rent_anything.common.model.ApiResponse;
import com.rajat.rent_anything.notification.EmailService;
import com.rajat.rent_anything.security.CustomUserDetails;
import com.rajat.rent_anything.security.jwt.JwtService;
import com.rajat.rent_anything.security.emailVerification.EmailVerificationService;
import com.rajat.rent_anything.security.passwordReset.PasswordResetService;
import com.rajat.rent_anything.security.refreshTokens.RefreshTokenEntity;
import com.rajat.rent_anything.security.refreshTokens.RefreshTokenService;
import com.rajat.rent_anything.user.application.UserService;
import com.rajat.rent_anything.user.domain.User;
import com.rajat.rent_anything.user.records.request.LoginRequest;
import com.rajat.rent_anything.user.records.response.AuthResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller exposing authentication and account-security endpoints.
 *
 * Responsibilities:
 * - User registration
 * - User login
 * - Access token refresh
 * - Email verification
 * - Password reset workflows
 *
 * All endpoints under /auth are publicly accessible and act as
 * entry points into the application's authentication system.
 */
@Slf4j
@RestController
@RequestMapping("/auth")
public class AuthController {

    /**
     * Handles user registration and account lifecycle operations.
     */
    private final UserService userService;

    /**
     * Spring Security component responsible for validating login credentials.
     */
    private final AuthenticationManager authenticationManager;

    /**
     * Service used for generating JWT access tokens.
     */
    private final JwtService jwtService;

    /**
     * Service responsible for refresh token creation, validation,
     * rotation, and revocation.
     */
    private final RefreshTokenService refreshTokenService;

    /**
     * Service responsible for email verification token management.
     */
    private final EmailVerificationService emailVerificationService;

    /**
     * Service responsible for sending application emails.
     */
    private final EmailService emailService;

    /**
     * Service responsible for password reset workflows.
     */
    private final PasswordResetService passwordResetService;

    public AuthController(UserService userService,
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            RefreshTokenService refreshTokenService,
            EmailVerificationService emailVerificationService,
            EmailService emailService, PasswordResetService passwordResetService) {
        this.userService = userService;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.emailVerificationService = emailVerificationService;
        this.emailService = emailService;
        this.passwordResetService = passwordResetService;
    }

    /**
     * Registers a new user account.
     *
     * Workflow:
     * 1. Create user account.
     * 2. Generate email verification token.
     * 3. Send verification email.
     * 4. Return newly created user id.
     *
     * Newly registered users must verify their email before
     * they can successfully authenticate.
     *
     * @param email    user's email address
     * @param password user's password
     * @return newly created user id
     */
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<Long>> signUp(
            @RequestParam("email") String email,
            @RequestParam("password") String password) {
        log.info("Sign up attempt for email: {}", email);
        Long newUserId = userService.signUp(email, password);
        String emailVerificationToken = emailVerificationService.createEmailVerificationToken(newUserId);
        String verificationLink = "http://localhost:8080/auth/verify-email?token=" + emailVerificationToken;
        emailService.sendEmail(
                email,
                "Verify your email for rentanything.com",
                "Click this link to verify your email and start RENTINGGGG...\n" + verificationLink);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(newUserId));
    }

    /**
     * Authenticates a user and issues authentication tokens.
     *
     * Workflow:
     * 1. Validate credentials using AuthenticationManager.
     * 2. Generate JWT access token.
     * 3. Generate refresh token.
     * 4. Return both tokens to the client.
     *
     * @param loginRequest login credentials
     * @return access token and refresh token
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@RequestBody LoginRequest loginRequest) {
        log.info("Login attempt for user: {}", loginRequest.email());
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.email(),
                        loginRequest.password()));
        log.info("Login successful for user: {}", authentication.getName());

        CustomUserDetails authenticatedUserDetails = (CustomUserDetails) authentication.getPrincipal();
        log.info("Authenticated user details: {}", authenticatedUserDetails.getDomainUser());

        String accessToken = jwtService.generateAccessToken(
                authenticatedUserDetails.getUsername(),
                authenticatedUserDetails.getAuthorities().iterator().next().getAuthority());
        RefreshTokenEntity refreshToken = refreshTokenService
                .createRefreshToken(authenticatedUserDetails.getDomainUser().getId());

        // TODO: Delete this log after testing
        log.info("Generated access token: {}, refresh token: {}", accessToken, refreshToken.getToken());
        return ResponseEntity.ok(ApiResponse.success(new AuthResponse(accessToken, refreshToken.getToken())));
    }

    /**
     * Generates a new access token using a valid refresh token.
     *
     * Refresh token rotation is performed as part of this process,
     * meaning the old refresh token is invalidated and replaced
     * with a newly generated token.
     *
     * @param refreshToken refresh token issued during login
     * @return new access token and rotated refresh token
     */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(@RequestParam("refreshToken") String refreshToken) {
        log.info("Refresh token attempt with token: {}", refreshToken);
        RefreshTokenEntity validRefreshToken = refreshTokenService
                .verifyAndRotateRefreshTokenIfFoundValid(refreshToken);
        User user = userService.getUserById(validRefreshToken.getUserId());
        String newAccessToken = jwtService.generateAccessToken(
                user.getEmail(),
                "ROLE_" + user.getRole().name());
        log.info("Refresh token successful for userId: {}, new access token: {}", user.getId(), newAccessToken);
        return ResponseEntity.ok(ApiResponse.success(new AuthResponse(newAccessToken, validRefreshToken.getToken())));
    }

    /**
     * Verifies a user's email address using a verification token.
     *
     * Successful verification marks the associated user account
     * as verified and invalidates the verification token.
     *
     * @param token email verification token
     * @return success response
     */
    @GetMapping("/verify-email")
    public ResponseEntity<ApiResponse<Void>> verifyEmail(@RequestParam("token") String token) {
        log.info("Email verification attempt with token: {}", token);
        Long userId = emailVerificationService.verifyEmail(token);
        userService.markUserAsEmailVerified(userId);
        log.info("Email verification successful for userId: {}", userId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    /**
     * Sends a new verification email to an unverified user.
     *
     * A new verification token is generated only when the
     * existing token is no longer valid.
     *
     * @param email user email address
     * @return success response
     */
    @PostMapping("/resend-verification-email")
    public ResponseEntity<ApiResponse<Void>> resendVerificationEmail(@RequestParam("email") String email) {
        User user = userService.findByEmail(email);
        if (user.isVerified()) {
            throw new IllegalArgumentException("Email is already verified");
        }
        String newVerificationToken = emailVerificationService.resendEmailVerificationToken(user.getId());
        String verificationLink = "http://localhost:8080/auth/verify-email?token=" + newVerificationToken;
        emailService.sendEmail(
                email,
                "Verify your email for rentanything.com",
                "Click this link to verify your email:\n" + verificationLink);
        log.info("Resent email verification token and email sent for user with email: {}", email);
        return ResponseEntity.ok(ApiResponse.success());
    }

    /**
     * Initiates the password reset workflow.
     *
     * Generates a password reset token and sends a password
     * reset email to the user.
     *
     * @param email user email address
     * @return success response
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(@RequestParam("email") String email) {
        User user = userService.findByEmail(email);
        String passwordResetToken = passwordResetService.createPasswordResetToken(user.getId());
        String resetPasswordLink = "http://localhost:8080/auth/reset-password?token=" + passwordResetToken;
        emailService.sendEmail(
                email,
                "Reset your password for rentanything.com",
                "Click this link to reset your password:\n" + resetPasswordLink);
        log.info("Password reset token generated and email sent for user with email: {}", email);
        return ResponseEntity.ok(ApiResponse.success());
    }

    /**
     * Completes the password reset process.
     *
     * The supplied token is validated before updating the
     * user's password. Tokens are invalidated after successful use.
     *
     * @param token       password reset token
     * @param newPassword new password chosen by the user
     * @return success response
     */
    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@RequestParam("token") String token,
            @RequestParam("newPassword") String newPassword) {
        passwordResetService.verifyPasswordResetTokenAndResetPassword(token, newPassword);
        log.info("Password reset successful for token: {}", token);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
