package com.rajat.rent_anything.security;

import com.rajat.rent_anything.user.domain.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Spring Security adapter for the application's User entity.
 *
 * <h2>Purpose</h2>
 * Spring Security does not understand application-specific domain models.
 * Instead, it works with objects implementing the {@link UserDetails} interface.
 *
 * This class acts as a bridge between:
 *
 * <ul>
 *     <li>Application Domain Model ({@link User})</li>
 *     <li>Spring Security Authentication Framework</li>
 * </ul>
 *
 * <h2>Why Not Use User Directly?</h2>
 *
 * The User entity is designed for business/domain logic and database persistence.
 *
 * Spring Security requires a standardized contract containing:
 * <ul>
 *     <li>Username</li>
 *     <li>Password</li>
 *     <li>Authorities/Roles</li>
 *     <li>Account status information</li>
 * </ul>
 *
 * Rather than polluting the domain entity with security-specific concerns,
 * this wrapper adapts the User object to Spring Security's expectations.
 *
 * <h2>Authentication Flow</h2>
 *
 * Login Request
 *      |
 *      v
 * User Entity Loaded
 *      |
 *      v
 * CustomUserDetails Created
 *      |
 *      v
 * AuthenticationManager
 *      |
 *      v
 * SecurityContextHolder
 *
 * <h2>Design Benefits</h2>
 * <ul>
 *     <li>Separates security concerns from domain concerns.</li>
 *     <li>Allows Spring Security integration without modifying User entity.</li>
 *     <li>Provides a centralized place for security-related mappings.</li>
 *     <li>Supports future account locking/expiration features.</li>
 * </ul>
 */
public class CustomUserDetails implements UserDetails {

    /**
     * Underlying domain user.
     *
     * This remains the source of truth for all user-related data.
     *
     * CustomUserDetails simply exposes selected properties
     * in a format understood by Spring Security.
     */
    private final User user;

    /**
     * Creates a security representation of the domain user.
     *
     * @param user authenticated domain user
     */
    public CustomUserDetails(User user) {
        this.user = user;
    }

    /**
     * Returns the original domain user.
     *
     * Useful when application-specific user information is required
     * beyond what UserDetails exposes.
     *
     * Example:
     * - User id
     * - Address
     * - Profile information
     * - Custom business fields
     *
     * @return underlying User entity
     */
    public User getDomainUser() {
        return user;
    }

    /**
     * Returns the unique username used during authentication.
     *
     * Design Decision:
     * Email is used as the username because it is unique
     * and easier for users to remember than generated identifiers.
     *
     * Spring Security internally uses this value as the
     * principal identifier.
     *
     * @return user's email address
     */
    @Override
    public String getUsername() {
        return user.getEmail();
    }

    /**
     * Returns the hashed password stored in the database.
     *
     * Important:
     * This should NEVER be a plain text password.
     *
     * During authentication Spring Security compares
     * the submitted password against this stored hash
     * using the configured PasswordEncoder.
     *
     * @return encrypted password hash
     */
    @Override
    public String getPassword() {
        return user.getPassword();
    }

    /**
     * Indicates whether the account has expired.
     *
     * Current Behavior:
     * Accounts never expire.
     *
     * Future Enhancement:
     * This can be connected to business rules such as:
     * - Subscription expiration
     * - Temporary accounts
     * - Compliance requirements
     *
     * @return true because account expiration is not implemented
     */
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    /**
     * Indicates whether the account is locked.
     *
     * Current Behavior:
     * Account locking is not implemented.
     *
     * Future Enhancement:
     * Could be used for:
     * - Excessive failed login attempts
     * - Administrative account suspension
     * - Fraud detection
     *
     * @return true because account locking is not implemented
     */
    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    /**
     * Indicates whether authentication credentials have expired.
     *
     * Current Behavior:
     * Password expiration is not enforced.
     *
     * Future Enhancement:
     * Could support:
     * - Mandatory password rotation
     * - Security policy enforcement
     * - Compliance requirements
     *
     * @return true because credential expiration is not implemented
     */
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    /**
     * Determines whether the account is enabled.
     *
     * Business Rule:
     * Only verified users are considered active and allowed
     * to authenticate successfully.
     *
     * Why Email Verification Matters:
     * - Prevents fake account usage.
     * - Confirms email ownership.
     * - Reduces spam and abuse.
     * - Improves platform trust.
     *
     * Authentication Impact:
     * If this method returns false,
     * Spring Security treats the account as disabled and
     * rejects authentication attempts.
     *
     * @return true if user has verified their email
     */
    @Override
    public boolean isEnabled() {
        return this.user.isVerified();
    }

    /**
     * Returns the user's granted authorities.
     *
     * <h2>What is an Authority?</h2>
     *
     * An authority represents a permission or role that
     * Spring Security uses during authorization.
     *
     * Examples:
     * - ROLE_USER
     * - ROLE_ADMIN
     * - ROLE_OWNER
     *
     * <h2>Why Prefix with ROLE_?</h2>
     *
     * Spring Security's role-based authorization conventions
     * expect roles to begin with the "ROLE_" prefix.
     *
     * Example:
     *
     * User Role Enum:
     * ADMIN
     *
     * Converted Authority:
     * ROLE_ADMIN
     *
     * This allows security expressions such as:
     *
     * hasRole("ADMIN")
     *
     * to work correctly.
     *
     * Authorization Examples:
     * - @PreAuthorize("hasRole('ADMIN')")
     * - hasRole("USER")
     * - hasAnyRole("ADMIN", "USER")
     *
     * @return collection of authorities granted to the user
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(
                new SimpleGrantedAuthority(
                        "ROLE_" + user.getRole().name()
                )
        );
    }
}
