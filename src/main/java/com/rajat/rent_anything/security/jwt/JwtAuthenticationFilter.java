package com.rajat.rent_anything.security.jwt;

import com.rajat.rent_anything.security.CustomUserDetails;
import com.rajat.rent_anything.security.CustomUserDetailsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Spring Security filter responsible for authenticating incoming requests
 * using JWT access tokens.
 *
 * <h2>Purpose</h2>
 * This filter executes for every incoming HTTP request and determines
 * whether the request contains a valid JWT token.
 *
 * If a valid token is found:
 * <ol>
 *     <li>Extract user information from token.</li>
 *     <li>Load latest user details from database.</li>
 *     <li>Create an Authentication object.</li>
 *     <li>Store Authentication in SecurityContext.</li>
 *     <li>Allow downstream security checks to use authenticated user.</li>
 * </ol>
 *
 * <h2>Why is this Filter Needed?</h2>
 * JWT authentication is stateless.
 *
 * Unlike session-based authentication where the server remembers logged-in users,
 * JWT-based systems require every request to prove its identity by sending
 * a valid token.
 *
 * Therefore every request must pass through a filter that:
 * <ul>
 *     <li>Reads the Authorization header.</li>
 *     <li>Validates the JWT signature.</li>
 *     <li>Reconstructs the authenticated user context.</li>
 * </ul>
 *
 * <h2>Authentication vs Authorization</h2>
 *
 * Authentication:
 * "Who are you?"
 *
 * Authorization:
 * "What are you allowed to do?"
 *
 * This filter performs Authentication.
 * Later Spring Security uses roles/authorities for Authorization.
 *
 * <h2>Request Flow</h2>
 *
 * Client Request
 *       |
 *       v
 * Authorization: Bearer <token>
 *       |
 *       v
 * JwtAuthenticationFilter
 *       |
 *       v
 * Token Validation
 *       |
 *       v
 * SecurityContextHolder
 *       |
 *       v
 * Controller / Protected Endpoint
 *
 * <h2>Why OncePerRequestFilter?</h2>
 *
 * Guarantees that this filter executes exactly once per request.
 *
 * This prevents duplicate authentication processing during request dispatches.
 */
@Component
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /**
     * Centralized JWT utility service.
     *
     * Responsible for:
     * - token validation
     * - claim extraction
     * - signature verification
     */
    private final JwtService jwtService;

    /**
     * Service used to load the most up-to-date user information.
     *
     * Design Decision:
     * Even though the JWT contains user information,
     * we still load the user from the database.
     *
     * Benefits:
     * - Fetch latest user details
     * - Detect deleted users
     * - Support future account locking/disabling
     * - Avoid trusting token for all user data
     */
    private final CustomUserDetailsService userDetailsService;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            CustomUserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    /**
     * Main authentication workflow executed for every request.
     *
     * Processing Steps:
     *
     * 1. Check Authorization header.
     * 2. Verify Bearer token exists.
     * 3. Validate JWT signature and expiration.
     * 4. Extract claims.
     * 5. Load user details.
     * 6. Create Authentication object.
     * 7. Store Authentication in SecurityContext.
     * 8. Continue filter chain.
     *
     * Security Note:
     * This method only establishes identity.
     * It does NOT decide endpoint access permissions.
     *
     * Authorization decisions happen later through:
     * - @PreAuthorize
     * - hasRole()
     * - SecurityFilterChain rules
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        /**
         * Retrieve Authorization header.
         *
         * Expected format:
         *
         * Authorization: Bearer eyJhbGciOi...
         */
        String header = request.getHeader("Authorization");

        /**
         * If no Bearer token is present,
         * skip authentication and allow request
         * to continue through the filter chain.
         *
         * Why not reject immediately?
         *
         * Some endpoints may intentionally be public.
         * Endpoint-level security configuration determines
         * whether authentication is required.
         */
        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        /**
         * Remove "Bearer " prefix.
         *
         * Example:
         *
         * Bearer abc.def.ghi
         *
         * becomes:
         *
         * abc.def.ghi
         */
        String token = header.substring(7);

        /**
         * Validate token integrity.
         *
         * Validation includes:
         * - Signature verification
         * - Expiration check
         * - Structure validation
         *
         * If validation fails, authentication is skipped.
         */
        if (!jwtService.isNotTamperedWithAndValid(token)) {
            filterChain.doFilter(request, response);
            return;
        }

        /**
         * Extract claims from validated token.
         *
         * At this point we trust these values because
         * signature verification has already succeeded.
         */
        String email = jwtService.extractEmail(token);
        String role = jwtService.extractRole(token);

        log.info("Extracted email from token: {}, role: {}", email, role);

        /**
         * Load user from database.
         *
         * Why not use only JWT data?
         *
         * Consider:
         * - User disabled after token creation
         * - User deleted
         * - Account status changed
         *
         * Database remains the source of truth.
         */
        CustomUserDetails userDetails =
                (CustomUserDetails) userDetailsService.loadUserByUsername(email);

        /**
         * Create Spring Security Authentication object.
         *
         * Components:
         *
         * Principal     -> authenticated user
         * Credentials   -> null (already authenticated)
         * Authorities   -> user's permissions/roles
         *
         * Once this object exists, Spring Security
         * considers the request authenticated.
         */
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        List.of(() -> role)
                );

        /**
         * Attach request-specific metadata.
         *
         * Common examples:
         * - Remote IP address
         * - Session information
         * - Request details
         *
         * Useful for auditing and debugging.
         */
        auth.setDetails(
                new WebAuthenticationDetailsSource()
                        .buildDetails(request)
        );

        /**
         * Store Authentication in SecurityContext.
         *
         * This is the most important line in the filter.
         *
         * SecurityContextHolder acts as the current
         * request's security container.
         *
         * Once populated:
         *
         * SecurityContextHolder.getContext()
         *     .getAuthentication()
         *
         * can be accessed anywhere in the request lifecycle.
         *
         * Controllers, services, and security rules can now
         * identify the currently authenticated user.
         */
        SecurityContextHolder.getContext().setAuthentication(auth);

        /**
         * Continue request processing.
         *
         * Important:
         * Filters should almost always continue the chain
         * unless intentionally blocking the request.
         */
        filterChain.doFilter(request, response);
    }
}