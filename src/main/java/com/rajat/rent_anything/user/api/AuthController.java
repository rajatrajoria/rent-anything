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

@Slf4j
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserService userService;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final EmailVerificationService emailVerificationService;
    private final EmailService emailService;
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

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<Long>> signUp(
            @RequestParam("email") String email,
            @RequestParam("password") String password) {
        log.info("Sign up attempt for email: {}", email);
        Long newUserId =  userService.signUp(email, password);
        String emailVerificationToken = emailVerificationService.createEmailVerificationToken(newUserId);
        String verificationLink = "http://localhost:8080/auth/verify-email?token=" + emailVerificationToken;
        emailService.sendEmail(
                email,
                "Verify your email for rentanything.com",
                "Click this link to verify your email:\n" + verificationLink);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(newUserId));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@RequestBody LoginRequest loginRequest) {
        log.info("Login attempt for user: {}", loginRequest.email());
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.email(),
                        loginRequest.password()
                )
        );
        log.info("Login successful for user: {}", authentication.getName());

        CustomUserDetails authenticatedUserDetails = (CustomUserDetails) authentication.getPrincipal();
        log.info("Authenticated user details: {}", authenticatedUserDetails.getDomainUser());

        String accessToken =  jwtService.generateAccessToken(
                authenticatedUserDetails.getUsername(),
                authenticatedUserDetails.getAuthorities().iterator().next().getAuthority()
        );
        RefreshTokenEntity refreshToken = refreshTokenService.createRefreshToken(authenticatedUserDetails.getDomainUser().getId());

        //TODO: Delete this log after testing
        log.info("Generated access token: {}, refresh token: {}", accessToken, refreshToken.getToken());
        return ResponseEntity.ok(ApiResponse.success(new AuthResponse(accessToken, refreshToken.getToken())));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(@RequestParam("refreshToken") String refreshToken) {
        log.info("Refresh token attempt with token: {}", refreshToken);
        RefreshTokenEntity validRefreshToken = refreshTokenService.verifyAndRotateRefreshTokenIfFoundValid(refreshToken);
        User user = userService.getUserById(validRefreshToken.getUserId());
        String newAccessToken = jwtService.generateAccessToken(
                user.getEmail(),
                "ROLE_" + user.getRole().name()
        );
        log.info("Refresh token successful for userId: {}, new access token: {}", user.getId(), newAccessToken);
        return ResponseEntity.ok(ApiResponse.success(new AuthResponse(newAccessToken, validRefreshToken.getToken())));
    }

    @PostMapping("/verify-email")
    public ResponseEntity<ApiResponse<Void>> verifyEmail(@RequestParam("token") String token){
        log.info("Email verification attempt with token: {}", token);
        Long userId = emailVerificationService.verifyEmail(token);
        userService.markUserAsEmailVerified(userId);
        log.info("Email verification successful for userId: {}", userId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @PostMapping("/resend-verification-email")
    public ResponseEntity<ApiResponse<Void>> resendVerificationEmail(@RequestParam("email") String email) {
        User user = userService.findByEmail(email);
        if(user.isVerified()) {
            throw new IllegalArgumentException("Email is already verified");
        }
        String newVerificationToken = emailVerificationService.resendEmailVerificationToken(user.getId());
        String verificationLink = "http://localhost:8080/auth/verify-email?token=" + newVerificationToken;
        emailService.sendEmail(
                email,
                "Verify your email for rentanything.com",
                "Click this link to verify your email:\n" + verificationLink
        );
        log.info("Resent email verification token and email sent for user with email: {}", email);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(@RequestParam("email") String email) {
        User user = userService.findByEmail(email);
        String passwordResetToken = passwordResetService.createPasswordResetToken(user.getId());
        String resetPasswordLink = "http://localhost:8080/auth/reset-password?token=" + passwordResetToken;
        emailService.sendEmail(
                email,
                "Reset your password for rentanything.com",
                "Click this link to reset your password:\n" + resetPasswordLink
        );
        log.info("Password reset token generated and email sent for user with email: {}", email);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@RequestParam("token") String token, @RequestParam("newPassword") String newPassword) {
        passwordResetService.verifyPasswordResetTokenAndResetPassword(token, newPassword);
        log.info("Password reset successful for token: {}", token);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
