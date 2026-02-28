package com.rajat.rent_anything.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.util.Date;

@Service
public class JwtService {

    private String secret;
    private long expiration;
    private final Key key;

    public JwtService(@Value("${jwt.secret.key}") String secret,
                      @Value("${jwt.access.token.expiration:3600000}") long expiration) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("Property `jwt.secret` must be set (use a base64 32-byte key)");
        }
        this.secret = secret;
        this.key = Keys.hmacShaKeyFor(secret.getBytes());
        this.expiration = expiration;
    }

    public String generateAccessToken(String email, String role) {
        return Jwts.builder()
                .setSubject(email)
                .claim("role", role)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(key)
                .compact();
    }

    public String extractEmail(String token) {
        return parse(token).getSubject();
    }

    public String extractRole(String token) {
        return parse(token).get("role", String.class);
    }

    public boolean isNotTamperedWithAndValid(String token) {
        try{
            parse(token);
            return true;
        } catch (JwtException e) {
            return false;
        }
    }

    private Claims parse(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
