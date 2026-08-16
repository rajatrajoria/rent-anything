package com.rajat.rent_anything.security.jwt;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtServiceTest {

    private static final String SECRET = "ThisIsASecretKeyForJwtTestingPurposesOnly123456";
    private static final long ONE_HOUR_MS = 3_600_000L;

    private JwtService jwtService() {
        return new JwtService(SECRET, ONE_HOUR_MS);
    }

    @Test
    void constructor_blankSecret_throwsIllegalStateException() {
        assertThrows(IllegalStateException.class, () -> new JwtService("   ", ONE_HOUR_MS));
    }

    @Test
    void constructor_nullSecret_throwsIllegalStateException() {
        assertThrows(IllegalStateException.class, () -> new JwtService(null, ONE_HOUR_MS));
    }

    @Test
    void generateAccessToken_thenExtractEmailAndRole_roundTrips() {
        JwtService jwtService = jwtService();

        String token = jwtService.generateAccessToken("user@example.com", "ROLE_USER");

        assertThat(jwtService.extractEmail(token)).isEqualTo("user@example.com");
        assertThat(jwtService.extractRole(token)).isEqualTo("ROLE_USER");
    }

    @Test
    void isNotTamperedWithAndValid_freshlyIssuedToken_returnsTrue() {
        JwtService jwtService = jwtService();

        String token = jwtService.generateAccessToken("user@example.com", "ROLE_USER");

        assertThat(jwtService.isNotTamperedWithAndValid(token)).isTrue();
    }

    @Test
    void isNotTamperedWithAndValid_tamperedPayload_returnsFalse() {
        JwtService jwtService = jwtService();
        String token = jwtService.generateAccessToken("user@example.com", "ROLE_USER");

        // Flip the last character of the signature segment to corrupt it.
        String tampered = token.substring(0, token.length() - 1)
                + (token.charAt(token.length() - 1) == 'a' ? 'b' : 'a');

        assertThat(jwtService.isNotTamperedWithAndValid(tampered)).isFalse();
    }

    @Test
    void isNotTamperedWithAndValid_garbageToken_returnsFalse() {
        JwtService jwtService = jwtService();

        assertThat(jwtService.isNotTamperedWithAndValid("not-a-jwt-at-all")).isFalse();
    }

    @Test
    void isNotTamperedWithAndValid_expiredToken_returnsFalse() {
        JwtService jwtService = new JwtService(SECRET, -1000L); // already expired at issuance

        String token = jwtService.generateAccessToken("user@example.com", "ROLE_USER");

        assertThat(jwtService.isNotTamperedWithAndValid(token)).isFalse();
    }

    @Test
    void extractEmail_expiredToken_throwsJwtException() {
        JwtService jwtService = new JwtService(SECRET, -1000L);

        String token = jwtService.generateAccessToken("user@example.com", "ROLE_USER");

        assertThrows(JwtException.class, () -> jwtService.extractEmail(token));
    }

    @Test
    void isNotTamperedWithAndValid_tokenSignedWithDifferentSecret_returnsFalse() {
        JwtService issuer = new JwtService(SECRET, ONE_HOUR_MS);
        JwtService verifier = new JwtService("ADifferentSecretKeyEntirelyForThisTest1234567890", ONE_HOUR_MS);

        String token = issuer.generateAccessToken("user@example.com", "ROLE_USER");

        assertThat(verifier.isNotTamperedWithAndValid(token)).isFalse();
    }
}
