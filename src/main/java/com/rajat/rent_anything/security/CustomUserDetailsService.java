package com.rajat.rent_anything.security;

import com.rajat.rent_anything.user.application.UserService;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
/**
 * Spring Security implementation responsible for loading users
 * during authentication and authorization workflows.
 *
 * <h2>Purpose</h2>
 * Spring Security does not know how users are stored in the application.
 *
 * This service acts as the integration point between:
 *
 * <ul>
 *     <li>Spring Security</li>
 *     <li>Application User Management Layer</li>
 * </ul>
 *
 * Whenever Spring Security needs to authenticate a user,
 * it delegates user retrieval to an implementation of
 * {@link UserDetailsService}.
 *
 * <h2>Authentication Flow</h2>
 *
 * Login Request
 *      |
 *      v
 * AuthenticationManager
 *      |
 *      v
 * UserDetailsService.loadUserByUsername()
 *      |
 *      v
 * UserService
 *      |
 *      v
 * Database
 *      |
 *      v
 * User Entity
 *      |
 *      v
 * CustomUserDetails
 *      |
 *      v
 * Spring Security Authentication
 *
 * <h2>JWT Authentication Flow</h2>
 *
 * This service is also used after JWT validation.
 *
 * Request
 *      |
 *      v
 * JWT Filter
 *      |
 *      v
 * Extract Email
 *      |
 *      v
 * loadUserByUsername(email)
 *      |
 *      v
 * SecurityContextHolder
 *
 * <h2>Why Not Query Repository Directly?</h2>
 *
 * The security layer depends on the UserService rather than
 * directly accessing the repository.
 *
 * Benefits:
 * <ul>
 *     <li>Maintains application layering.</li>
 *     <li>Keeps business rules centralized.</li>
 *     <li>Avoids security layer coupling to persistence implementation.</li>
 *     <li>Makes future changes easier.</li>
 * </ul>
 *
 * <h2>Why Return CustomUserDetails?</h2>
 *
 * Spring Security requires objects implementing
 * {@link org.springframework.security.core.userdetails.UserDetails}.
 *
 * CustomUserDetails adapts the application's User entity
 * into Spring Security's expected format.
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    /**
     * Application service responsible for user retrieval.
     *
     * The UserService remains the source of truth for
     * user-related operations and business rules.
     *
     * Design Decision:
     * Depend on the service layer rather than repositories
     * to preserve clean architecture boundaries.
     */
    private final UserService userService;

    /**
     * Constructor-based dependency injection.
     *
     * Spring automatically provides the UserService bean.
     *
     * @param userService application user management service
     */
    public CustomUserDetailsService(UserService userService) {
        this.userService = userService;
    }

    /**
     * Loads a user by email and converts it into a Spring Security
     * compatible UserDetails implementation.
     *
     * <h2>Who Calls This Method?</h2>
 *
 * This method is typically invoked by:
 * <ul>
 *     <li>AuthenticationManager during login.</li>
 *     <li>JWT filters when rebuilding authentication context.</li>
 *     <li>Spring Security internals whenever user details are needed.</li>
 * </ul>
 *
 * <h2>Why Email Instead of Username?</h2>
 *
 * In this application, email serves as the unique user identifier.
 * Therefore Spring Security's "username" concept maps directly
 * to the user's email address.
 *
 * <h2>Process</h2>
 *
 * 1. Find user by email.
 * 2. Validate user existence.
 * 3. Wrap User entity inside CustomUserDetails.
 * 4. Return security-compatible object.
 *
 * <h2>Why Wrap the User?</h2>
 *
 * Spring Security expects:
 * - username
 * - password
 * - authorities
 * - account status
 *
 * The User entity alone does not implement this contract.
 *
 * @param email unique email identifier of the user
 * @return Spring Security representation of the user
 * @throws UsernameNotFoundException if user cannot be found
 */
    @Override
    public CustomUserDetails loadUserByUsername(String email)
            throws UsernameNotFoundException {

        /**
         * Retrieve user from application layer.
         *
         * Expected behavior:
         * UserService should throw an exception when no user
         * exists for the supplied email.
         */
        var user = userService.findByEmail(email);

        /**
         * Convert domain user into a Spring Security compatible object.
         *
         * This adapter allows Spring Security to access:
         * - password
         * - authorities
         * - account status
         * - principal information
         */
        return new CustomUserDetails(user);
    }
}
