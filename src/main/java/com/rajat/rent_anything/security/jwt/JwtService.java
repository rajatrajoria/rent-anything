package com.rajat.rent_anything.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.util.Date;

/**
 * Service responsible for creating, parsing, and validating JSON Web Tokens (JWT).
 *
 * <h2>What is a JWT?</h2>
 * JWT (JSON Web Token) is a compact, URL-safe token format commonly used for
 * stateless authentication and authorization between clients and servers.
 *
 * <p>A JWT typically consists of three parts:
 * <pre>
 * Header.Payload.Signature
 * </pre>
 *
 * <ul>
 *     <li><b>Header</b> - Contains metadata such as signing algorithm.</li>
 *     <li><b>Payload</b> - Contains claims (user information).</li>
 *     <li><b>Signature</b> - Prevents token tampering.</li>
 * </ul>
 *
 * <h2>Purpose of this Service</h2>
 * This service centralizes all JWT-related operations:
 * <ul>
 *     <li>Generate access tokens after successful authentication.</li>
 *     <li>Extract user information from tokens.</li>
 *     <li>Validate token authenticity and expiration.</li>
 *     <li>Verify tokens have not been modified by clients.</li>
 * </ul>
 *
 * <h2>Security Model</h2>
 * Tokens are signed using an HMAC secret key.
 *
 * <p>The server never stores generated tokens. Instead:
 * <ol>
 *     <li>Token is issued after login.</li>
 *     <li>Client stores token.</li>
 *     <li>Client sends token with every request.</li>
 *     <li>Server verifies token signature.</li>
 * </ol>
 *
 * This approach enables stateless authentication because user sessions do not
 * need to be stored in a database or memory.
 *
 * <h2>Claims Stored</h2>
 * Current implementation stores:
 * <ul>
 *     <li>Email (JWT subject)</li>
 *     <li>User role</li>
 * </ul>
 *
 * Additional claims can be added in future without changing the authentication flow.
 */
@Service
public class JwtService {

    /**
     * Raw secret key value loaded from application configuration.
     *
     * Used to generate the cryptographic signing key.
     *
     * Security Note:
     * This secret should never be committed to source control.
     */
    private String secret;

    /**
     * Access token validity duration in milliseconds.
     *
     * Example:
     * 3600000 ms = 1 hour
     *
     * After expiration, clients must obtain a new token by re-authenticating
     * or using a refresh token mechanism.
     */
    private long expiration;

    /**
     * Cryptographic key used to sign and verify JWTs.
     *
     * Why store this as a field?
     * Creating the key once during application startup is more efficient
     * than recreating it for every request.
     *
     * The same key must be used for:
     * - Token generation
     * - Token validation
     *
     * Otherwise signatures will not match.
     */
    private final Key key;

    /**
     * Creates and initializes JWT infrastructure.
     *
     * Configuration Values:
     * - jwt.secret.key
     * - jwt.access.token.expiration
     *
     * Startup Validation:
     * Application startup fails if no secret key is provided because
     * JWT security would be impossible without a signing key.
     *
     * Design Decision:
     * Fail-fast during startup rather than allowing authentication
     * failures at runtime.
     *
     * @param secret JWT signing secret from configuration
     * @param expiration token expiration duration in milliseconds
     */
    public JwtService(@Value("${jwt.secret.key}") String secret,
                      @Value("${jwt.access.token.expiration:3600000}") long expiration) {

        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                "Property `jwt.secret` must be set (use a base64 32-byte key)"
            );
        }

        this.secret = secret;

        /**
         * Generates HMAC signing key.
         *
         * Why HMAC?
         * - Fast
         * - Simple to configure
         * - Widely used for internal APIs
         * - Requires only one secret key
         *
         * Alternative approaches:
         * - RSA (public/private key pair)
         * - EC cryptography
         */
        this.key = Keys.hmacShaKeyFor(secret.getBytes());

        this.expiration = expiration;
    }

    /**
     * Generates a signed JWT access token.
     *
     * Token Contents:
     * - Subject = user's email
     * - Custom claim = role
     * - Issued timestamp
     * - Expiration timestamp
     *
     * Example Payload:
     * {
     *   "sub": "user@example.com",
     *   "role": "ADMIN",
     *   "iat": 1712345678,
     *   "exp": 1712349278
     * }
     *
     * Authentication Flow:
     * 1. User logs in successfully.
     * 2. Server generates token.
     * 3. Client stores token.
     * 4. Client sends token on future requests.
     *
     * @param email authenticated user's email
     * @param role user's role used for authorization
     * @return signed JWT access token
     */
    public String generateAccessToken(String email, String role) {
        return Jwts.builder()
                .setSubject(email)
                .claim("role", role)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(key)
                .compact();
    }

    /**
     * Extracts the email from the JWT subject field.
     *
     * JWT Standard:
     * "sub" (subject) is intended to uniquely identify the token owner.
     *
     * In this application, the email serves as the user's unique identifier.
     *
     * @param token JWT token
     * @return user email stored in token
     */
    public String extractEmail(String token) {
        return parse(token).getSubject();
    }

    /**
     * Extracts the role claim from the token.
     *
     * Role information is later used by Spring Security to determine
     * whether a user is authorized to access specific endpoints.
     *
     * Example values:
     * - USER
     * - ADMIN
     *
     * @param token JWT token
     * @return role stored inside token
     */
    public String extractRole(String token) {
        return parse(token).get("role", String.class);
    }

    /**
     * Verifies token validity.
     *
     * Validation includes:
     * - Signature verification
     * - Token structure validation
     * - Expiration validation
     *
     * Why catch JwtException?
     * The JWT library throws various exceptions for:
     * - Expired tokens
     * - Invalid signatures
     * - Malformed tokens
     * - Unsupported token formats
     *
     * Rather than exposing implementation-specific exceptions,
     * this method provides a simple boolean result.
     *
     * @param token JWT token
     * @return true if token is valid and untampered
     */
    public boolean isNotTamperedWithAndValid(String token) {
        try {
            parse(token);
            return true;
        } catch (JwtException e) {
            return false;
        }
    }

    /**
     * Parses and validates a JWT.
     *
     * This is the core verification method used by all token-reading operations.
     *
     * Validation Steps:
     * 1. Verify JWT format.
     * 2. Verify signature using configured key.
     * 3. Verify token has not expired.
     * 4. Extract claims payload.
     *
     * Security Guarantee:
     * If parsing succeeds, the payload can be trusted because
     * any modification to the token would invalidate the signature.
     *
     * Design Decision:
     * Centralizing parsing logic avoids duplicated validation code
     * across multiple methods.
     *
     * @param token JWT token to validate
     * @return validated JWT claims
     * @throws JwtException when token is invalid, expired, malformed,
     *                      or signature verification fails
     */
    private Claims parse(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}