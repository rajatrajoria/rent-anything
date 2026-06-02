package com.rajat.rent_anything.security;

import com.rajat.rent_anything.security.jwt.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Central Spring Security configuration for the application.
 *
 * <h2>Purpose</h2>
 *
 * This class defines:
 * <ul>
 *     <li>Authentication strategy</li>
 *     <li>Authorization rules</li>
 *     <li>Password encoding mechanism</li>
 *     <li>Security filter chain</li>
 *     <li>JWT integration</li>
 * </ul>
 *
 * <h2>Security Architecture</h2>
 *
 * The application uses:
 * <ul>
 *     <li>JWT-based Authentication</li>
 *     <li>Stateless Security</li>
 *     <li>Role-based Authorization</li>
 * </ul>
 *
 * Unlike traditional session-based authentication,
 * the server does not maintain login sessions.
 *
 * Every request must provide a valid JWT token.
 *
 * <h2>High-Level Request Flow</h2>
 *
 * Client Request
 *      |
 *      v
 * Authorization Header
 *      |
 *      v
 * JwtAuthenticationFilter
 *      |
 *      v
 * SecurityContextHolder
 *      |
 *      v
 * Authorization Rules
 *      |
 *      v
 * Controller
 *
 * <h2>Why Stateless Authentication?</h2>
 *
 * Benefits:
 * <ul>
 *     <li>No server-side session storage.</li>
 *     <li>Horizontally scalable.</li>
 *     <li>Works well with REST APIs.</li>
 *     <li>Suitable for mobile and SPA clients.</li>
 * </ul>
 *
 * Trade-Off:
 * Token revocation becomes more difficult compared
 * to traditional session-based authentication.
 */
@Configuration
public class SecurityConfig {

    /**
     * Defines the application's security filter chain.
     *
     * <h2>What is a Security Filter Chain?</h2>
     *
     * Every incoming request passes through a sequence
     * of security-related filters before reaching controllers.
     *
     * Typical responsibilities:
     * - Authentication
     * - Authorization
     * - CSRF protection
     * - Session management
     * - Security context population
     *
     * <h2>Processing Order</h2>
     *
     * Incoming Request
     *      |
     *      v
     * JwtAuthenticationFilter
     *      |
     *      v
     * Spring Security Authorization
     *      |
     *      v
     * Controller
     *
     * @param http Spring Security HTTP configuration builder
     * @param jwtAuthenticationFilter JWT authentication filter
     * @return configured security filter chain
     * @throws Exception if configuration fails
     */
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter)
            throws Exception {

        http

                /**
                 * CSRF protection disabled.
                 *
                 * Why?
                 *
                 * CSRF primarily protects session-based applications.
                 *
                 * Since this application uses stateless JWT authentication
                 * and does not rely on browser-managed sessions,
                 * CSRF protection is generally unnecessary.
                 *
                 * If cookie-based authentication is introduced in future,
                 * this decision should be revisited.
                 */
                .csrf(csrf -> csrf.disable())

                /**
                 * Session management configuration.
                 *
                 * STATELESS means:
                 *
                 * - Spring Security will not create sessions.
                 * - Spring Security will not store authentication state.
                 * - Every request must authenticate independently.
                 *
                 * This is the recommended approach for JWT-based APIs.
                 */
                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )

                /**
                 * Authorization rules.
                 *
                 * Determines which endpoints require authentication.
                 *
                 * Current Rules:
                 *
                 * Public Endpoints:
                 * - auth/**
                 * - items/search
                 *
                 * Protected Endpoints:
                 * - Everything else
                 *
                 * Important:
                 * Authentication answers:
                 * "Who are you?"
                 *
                 * Authorization answers:
                 * "What are you allowed to access?"
                 */
                .authorizeHttpRequests(auth -> auth

                        /**
                         * Public endpoints.
                         *
                         * Users can access these endpoints
                         * without providing a JWT token.
                         *
                         * Common examples:
                         * - Login
                         * - Registration
                         * - Public search APIs
                         */
                        .requestMatchers("auth/**", "items/search")
                        .permitAll()

                        /**
                         * Catch-all security rule.
                         *
                         * Any endpoint not explicitly marked
                         * as public requires authentication.
                         *
                         * This follows a secure-by-default approach.
                         */
                        .anyRequest()
                        .authenticated()
                )

                /**
                 * Register JWT filter.
                 *
                 * Why before UsernamePasswordAuthenticationFilter?
                 *
                 * Spring Security's default login filter expects
                 * username/password authentication.
                 *
                 * Our application uses JWT tokens instead.
                 *
                 * Therefore JWT authentication must happen first
                 * so the SecurityContext is already populated
                 * before Spring Security performs authorization checks.
                 *
                 * Request Flow:
                 *
                 * Request
                 *     |
                 *     v
                 * JwtAuthenticationFilter
                 *     |
                 *     v
                 * SecurityContextHolder
                 *     |
                 *     v
                 * Authorization Checks
                 */
                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }

    /**
     * Password encoder used throughout the application.
     *
     * <h2>Why BCrypt?</h2>
     *
     * Passwords should NEVER be stored in plain text.
     *
     * BCrypt provides:
     * <ul>
     *     <li>Salted hashing</li>
     *     <li>Adaptive work factor</li>
     *     <li>Resistance against rainbow table attacks</li>
     * </ul>
     *
     * Example:
     *
     * Password:
     * myPassword123
     *
     * Stored Value:
     * $2a$10$...
     *
     * During login, Spring Security compares the submitted password
     * against the stored hash using this encoder.
     *
     * @return BCrypt password encoder bean
     */
    @Bean
    public BCryptPasswordEncoder bcryptPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Exposes Spring Security's AuthenticationManager.
     *
     * <h2>What is AuthenticationManager?</h2>
     *
     * AuthenticationManager is the central component responsible
     * for validating login credentials.
     *
     * Typical Login Flow:
     *
     * Login Request
     *      |
     *      v
     * AuthenticationManager
     *      |
     *      v
     * UserDetailsService
     *      |
     *      v
     * PasswordEncoder
     *      |
     *      v
     * Authentication Result
     *
     * Common Usage:
     *
     * During login endpoints:
     *
     * authenticationManager.authenticate(...)
     *
     * If authentication succeeds,
     * a JWT token is usually generated and returned.
     *
     * @param config Spring Security authentication configuration
     * @return AuthenticationManager bean
     * @throws Exception if manager creation fails
     */
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config)
            throws Exception {

        return config.getAuthenticationManager();
    }
}