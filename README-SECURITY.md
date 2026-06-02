# Security Module

## Overview

This module provides the complete authentication and authorization infrastructure for the application.

Features include:

* JWT-based Authentication
* Refresh Token Management
* Email Verification
* Password Reset
* Stateless Security
* Role-based Authorization
* BCrypt Password Hashing

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
* Defines public and protected endpoints
* Provides AuthenticationManager bean
* Provides BCryptPasswordEncoder bean

### Public Endpoints

```text
auth/**
items/search
```

### Protected Endpoints

```text
All other endpoints require authentication.
```

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
* Refresh Tokens
* Refresh Token Rotation
* Refresh Token Revocation
* Email Verification
* Password Reset
* BCrypt Password Hashing
* Role-Based Authorization
* Stateless Authentication

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
