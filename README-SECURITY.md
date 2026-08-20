# Security Module

## Overview

This module provides the complete authentication and authorization infrastructure for the application.

Features include:

* JWT-based Authentication
* CORS Configuration
* Refresh Token Management
* Email Verification
* Password Reset
* Stateless Security
* Role-based Authorization
* BCrypt Password Hashing
* Object Storage Access Control (item photos public, KYC documents never)

The application follows a stateless authentication model where user identity is carried through JWT access tokens instead of server-side sessions.

---

# Security Architecture

## Authentication Flow

```text
User Login
    |
    v
AuthenticationManager
    |
    v
CustomUserDetailsService
    |
    v
BCrypt Password Verification
    |
    v
JWT Access Token Generated
+
Refresh Token Generated
    |
    v
Tokens Returned To Client
```

---

## Protected Request Flow

```text
Client Request
    |
    v
Authorization: Bearer <access-token>
    |
    v
JwtAuthenticationFilter
    |
    v
JWT Validation
    |
    v
SecurityContextHolder
    |
    v
Authorization Check
    |
    v
Controller
```

---

# Core Components

## SecurityConfig

Central Spring Security configuration.

Responsibilities:

* Configures stateless authentication
* Registers JWT authentication filter
* Configures CORS (see below)
* Defines public and protected endpoints
* Provides AuthenticationManager bean
* Provides BCryptPasswordEncoder bean

### Public Endpoints

```text
auth/**
items/search
GET items/*
GET items/*/images
GET items/*/thumbnail
```

### Protected Endpoints

```text
All other endpoints require authentication.
```

### Role-Gated Endpoints (not a `SecurityConfig` matcher)

```text
admin/**        — class-level @PreAuthorize("hasRole('ADMIN')") on AdminControllers
admin/kyc/**    — same annotation, independently, on AdminKycController
```

`SecurityConfig`'s `authorizeHttpRequests` only distinguishes "public" from "any authenticated user" — there's no `.requestMatchers("/admin/**").hasRole("ADMIN")` rule. Admin-only protection is enforced entirely at the controller level via `@PreAuthorize`, which means a *new* admin controller has to remember to add the annotation itself; nothing in `SecurityConfig` would catch its absence. Both existing admin controllers (`AdminControllers` for user/trust management, `AdminKycController` for KYC review) follow this same convention independently — worth keeping consistent if a third one is ever added.

---

## CORS

### Why it exists

The frontend (a separate Next.js app, typically `http://localhost:3000` in dev) and this API (`http://localhost:8080`) are different origins. Browsers block cross-origin `fetch`/`XHR` calls by default unless the server explicitly opts in via CORS response headers — and **Spring Security ignores CORS entirely unless a `CorsConfigurationSource` bean is both defined and wired into the filter chain**. For a stretch of this project's history neither was true, so every browser-originated request to this API was silently blocked; nothing was wrong with the API itself, only the missing opt-in.

### How it's configured

```java
@Value("${app.cors.allowed-origins:http://localhost:3000}")
private List<String> allowedOrigins;

@Bean
public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(allowedOrigins);
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
}
```

...and, critically, wired into the filter chain:

```java
http.cors(cors -> cors.configurationSource(corsConfigurationSource()))
```

`app.cors.allowed-origins` accepts a comma-separated list, so a deployed environment can allow both a staging and production frontend origin without a code change — just an env var (`CORS_ALLOWED_ORIGINS`).

### Why no `allowCredentials`

This API authenticates via a bearer token in the `Authorization` header (`JwtAuthenticationFilter`), never cookies — so `allowCredentials(true)` is unnecessary. If cookie-based auth is ever introduced, this decision (and the CSRF-disabled decision below) both need revisiting together.

---

## JwtService

Handles all JWT-related operations.

### Responsibilities

* Generate access tokens
* Validate tokens
* Extract user claims
* Verify token signatures

### Stored Claims

| Claim | Description     |
| ----- | --------------- |
| sub   | User Email      |
| role  | User Role       |
| iat   | Issued Time     |
| exp   | Expiration Time |

### A lesson from a real config-key bug

`JwtService`'s constructor reads the access-token lifetime via `@Value("${jwt.access-token.expiration}")` — that string must match `application.yaml`'s nested path (`jwt: access-token: expiration:`) **exactly**. For a while it didn't: the code asked for `jwt.access.token.expiration` (dots instead of a hyphenated segment), which doesn't exist in the yaml. `@Value` does exact-key resolution with no compile-time check, and a `:3600000` default silently absorbed the mismatch — the configured value in `application.yaml` was never actually being read, and nothing about it looked broken. The fix was two-fold: correct the key, *and* remove the default, so a future mismatch of this kind fails loudly at startup (`Could not resolve placeholder`) instead of quietly using a fallback. This is the second time this exact bug shape has hit this codebase (`RefreshTokenService`/`PasswordResetService` had it earlier) — worth a quick grep for `@Value("...:default")` next to a yaml path whenever new config is added, since the default is exactly what lets a mismatch hide.

---

## JwtAuthenticationFilter

Executed once for every incoming request.

### Responsibilities

1. Read Authorization header.
2. Extract JWT token.
3. Validate token.
4. Load user details.
5. Create Authentication object.
6. Populate SecurityContextHolder.
7. Continue request processing.

---

## CustomUserDetails

Adapter between the application's User entity and Spring Security.

### Responsibilities

Provides:

* Username (Email)
* Password
* User Authorities
* Account Status

### Role Mapping

```text
USER  -> ROLE_USER
ADMIN -> ROLE_ADMIN
```

---

## CustomUserDetailsService

Spring Security integration point responsible for loading users.

### Responsibilities

* Retrieve users by email
* Convert User entities into CustomUserDetails
* Provide user information during authentication

---

# Access Tokens

## Purpose

Access tokens are JWTs used to authenticate API requests.

### Characteristics

* Stateless
* Short-lived
* Not stored in database
* Sent with every protected request
* Cryptographically signed

### Example

```http
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

---

# Refresh Tokens

## Purpose

Refresh tokens allow clients to obtain new access tokens without requiring users to log in again.

Unlike access tokens, refresh tokens are stored in the database.

---

## Refresh Token Flow

```text
Login
   |
   v
Refresh Token Created
   |
   v
Client Stores Token
   |
   v
Access Token Expires
   |
   v
Refresh Request
   |
   v
Refresh Token Validation
   |
   v
New Access Token
+
New Refresh Token
```

---

## RefreshTokenService

### Responsibilities

#### createRefreshToken()

Creates a refresh token for a user.

#### verifyAndRotateRefreshTokenIfFoundValid()

Validates a refresh token and performs token rotation.

#### revokeRefreshToken()

Revokes a specific refresh token.

#### revokeUserTokens()

Revokes all refresh tokens belonging to a user.

---

## Refresh Token Rotation

The application uses refresh token rotation.

```text
Old Refresh Token
       |
       v
Validated
       |
       v
Deleted
       |
       v
New Refresh Token Issued
```

Benefits:

* Prevents token reuse
* Improves security
* Reduces replay attack risk

---

# Email Verification

## Purpose

Email verification ensures that users own the email addresses they register with.

### Benefits

* Prevents fake registrations
* Confirms email ownership
* Reduces spam accounts
* Enables trusted communication

---

## Email Verification Flow

```text
User Registration
       |
       v
Verification Token Generated
       |
       v
Verification Email Sent
       |
       v
User Clicks Link
       |
       v
Token Validation
       |
       v
User Verified
       |
       v
Token Deleted
```

---

## EmailVerificationService

### Responsibilities

#### createEmailVerificationToken()

Creates a verification token.

#### resendEmailVerificationToken()

Generates a new token when the previous token has expired.

#### verifyEmail()

Validates a verification token and returns the associated user.

---

## Verification Token Rules

* One-time use
* Configurable expiration
* Deleted after successful verification
* Cannot be reused

---

# Password Reset

## Purpose

Allows users to securely reset forgotten passwords.

---

## Password Reset Flow

```text
Forgot Password
      |
      v
Reset Token Generated
      |
      v
Email Sent
      |
      v
User Opens Link
      |
      v
Token Validation
      |
      v
Password Updated
      |
      v
Token Deleted
```

---

## PasswordResetService

### Responsibilities

#### createPasswordResetToken()

Creates a password reset token.

#### verifyPasswordResetTokenAndResetPassword()

Validates the token and updates the user's password.

---

## Password Reset Rules

* One active token per user
* Time-limited validity
* One-time use
* Deleted after successful reset

---

# Object Storage Access Control

Two contexts (`item`, `kyc`) upload files to the same S3-compatible bucket via the `io.minio` client SDK, which works against local MinIO, Backblaze B2, Cloudflare R2, or AWS S3 itself — purely a matter of which `minio.endpoint`/`minio.region`/`minio.access-key`/`minio.secret-key`/`minio.bucket-name` values are configured, no code difference. Neither this app nor its infra sets a bucket-level public-read policy, so **the entire public-vs-private distinction between item photos and KYC documents lives in application code, not storage configuration**:

| | Item photos | KYC documents |
|---|---|---|
| Key prefix | `items/{itemId}/{uuid}.{ext}` | `kyc/{userId}/{kind}/{uuid}.{ext}` |
| Who can request a presigned URL | Anyone — `GET /items/{id}/images` and `/thumbnail` are `permitAll()` | Only the submitting user (`GET /kyc/me`) or an admin (`/admin/kyc/**`) — never a public route |
| Presigned URL expiry | 1 hour | 1 hour |
| Storage service | `ImageStorageService` → `MinioImageStorageService` | `KycDocumentStorageService` → `MinioKycDocumentStorageService` (a separate interface — see `README-KYC.md` for why the item one wasn't reused) |

**The practical implication:** if a future endpoint ever needs to expose a KYC document URL, the safety of that document depends entirely on that endpoint's own auth check being correct — there's no bucket policy backstop. Any new route that calls `KycDocumentStorageService.getDocumentUrl(...)` must be authenticated and scoped to the right user/admin before it's added to `SecurityConfig`'s public matcher list (or, better, never added to it at all).

---

# Database Tables

The security module uses the following tables.

## token_schema.refresh_tokens

Stores refresh tokens.

### Fields

* id
* userId
* token
* createdAt
* expiryAt
* isRevoked

---

## token_schema.email_verification_tokens

Stores email verification tokens.

### Fields

* id
* userId
* token
* createdAt
* updatedAt
* expiresAt

---

## token_schema.password_reset_tokens

Stores password reset tokens.

### Fields

* id
* userId
* token
* createdAt
* expiryDate

---

# Password Security

Passwords are stored using BCrypt hashing.

Benefits:

* Salted hashes
* Adaptive work factor
* Resistant to rainbow table attacks

Passwords are never stored in plaintext.

---

# Stateless Authentication

The application intentionally avoids server-side sessions.

### Traditional Session-Based Authentication

```text
Client
   |
   v
Session ID
   |
   v
Server Session Store
```

### JWT-Based Authentication

```text
Client
   |
   v
JWT Access Token
   |
   v
Server Validation
```

Benefits:

* Horizontal scalability
* REST-friendly design
* No session replication
* Reduced server memory usage

---

# Security Features

Implemented:

* JWT Authentication
* CORS (explicit allow-list, no credentials mode)
* Refresh Tokens
* Refresh Token Rotation
* Refresh Token Revocation
* Email Verification
* Password Reset
* BCrypt Password Hashing
* Role-Based Authorization (`@PreAuthorize`, applied independently by each admin controller)
* Stateless Authentication
* Object storage access control enforced in application code (see above)

---

# Package Structure

```text
security
│
├── SecurityConfig
│
├── CustomUserDetails
├── CustomUserDetailsService
│
├── jwt
│   ├── JwtService
│   └── JwtAuthenticationFilter
│
├── refreshTokens
│   ├── RefreshTokenEntity
│   ├── RefreshTokenRepository
│   └── RefreshTokenService
│
├── emailVerification
│   ├── EmailVerificationTokenEntity
│   ├── EmailVerificationTokenRepository
│   └── EmailVerificationService
│
└── passwordReset
    ├── PasswordResetTokenEntity
    ├── PasswordResetTokenRepository
    └── PasswordResetService
```
