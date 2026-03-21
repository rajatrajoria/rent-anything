# User Service Documentation (Advanced)

## Overview

The User Service manages:

* User lifecycle (signup, profile, password)
* Authentication (JWT + refresh tokens)
* Email verification
* Password reset
* Admin operations (trust management)
* Security enforcement

This service acts as the **identity and access control layer** of the system.

---

## Architecture

```
Client
  │
  ▼
Security Filter (JWT)
  │
  ▼
Controllers (Auth / User / Admin)
  │
  ▼
Services (User / Token / Security)
  │
  ├────────► UserRepository
  ├────────► Token Repositories
  └────────► External Email Service
  │
  ▼
Database
```

---

## API Documentation

### 🔐 Authentication APIs

#### 1. Signup

**POST** `/auth/signup`

Flow:

* Create user
* Generate email verification token
* Send verification email

---

#### 2. Login

**POST** `/auth/login`

Returns:

* Access Token (JWT)
* Refresh Token

---

#### 3. Refresh Token

**POST** `/auth/refresh`

* Validates refresh token
* Rotates refresh token
* Returns new access + refresh token

---

#### 4. Verify Email

**POST** `/auth/verify-email`

---

#### 5. Resend Verification Email

**POST** `/auth/resend-verification-email`

---

#### 6. Forgot Password

**POST** `/auth/forgot-password`

---

#### 7. Reset Password

**POST** `/auth/reset-password`

---

### 👤 User APIs

#### Get Profile

**GET** `/users/me`

#### Update Password

**PUT** `/users/password`

#### Logout

**POST** `/users/logout`

#### Logout All Devices

**POST** `/users/logoutAll`

---

### 🛡️ Admin APIs

#### Update Trust Status

**PATCH** `/admin/{userId}/trust-status`

* Only ADMIN role allowed

---

## Core Flows

### Signup + Email Verification

```
User → Signup
  → User created
  → Verification token generated
  → Email sent
  → User clicks link
  → Token verified
  → User marked verified
```

---

### Login Flow

```
User → Login
  → AuthenticationManager
  → Generate JWT
  → Create refresh token
  → Return tokens
```

---

### Token Refresh Flow

```
Client → /refresh
  → Validate refresh token
  → Delete old token
  → Create new refresh token
  → Generate new access token
```

---

### Password Reset Flow

```
User → Forgot Password
  → Generate token
  → Send email
  → User submits token
  → Validate token
  → Update password
  → Delete token
```

---

## JWT Security Flow

```
Incoming Request
  │
  ▼
JwtAuthenticationFilter
  │
  ├─ Extract token
  ├─ Validate token
  ├─ Extract email + role
  ├─ Load user
  └─ Set SecurityContext
  │
  ▼
Controller executes
```

---

## Token Strategy

### Access Token

* Short-lived
* Stateless

### Refresh Token

* Stored in DB
* Rotated on each use
* Revoked on logout/password change

### Email Verification Token

* One-time use
* Expiry-based

### Password Reset Token

* One-time use
* Single active token per user

---

## Security Design Decisions

### 1. JWT-based Stateless Auth

* No server sessions
* Scalable

### 2. Refresh Token Rotation

* Prevents replay attacks
* Improves security

### 3. Token Revocation

* Logout invalidates tokens
* Password change revokes all sessions

### 4. Trust Gate System

* Only trusted users can perform sensitive actions

---

## Business Rules

* Email must be unique
* Password must match during change
* Only admin can update trust status
* Users cannot modify their own trust status

---

## Edge Cases

* Expired tokens
* Reused tokens
* Invalid credentials
* Concurrent token usage

---

## Performance Considerations

* JWT avoids DB lookup for every request
* Refresh tokens allow long sessions safely
* Token tables indexed by userId

---

## Attack Scenarios & Security Analysis

### 1. Stolen Refresh Token

**Scenario:**
An attacker gains access to a valid refresh token.

**Mitigation in this system:**

* Refresh tokens are **rotated on every use**
* Old token is immediately invalidated
* If attacker uses old token → request fails

**Result:**

* Limits attacker to **single-use window**

---

### 2. Replay Attacks

**Scenario:**
Attacker reuses a previously intercepted token.

**Mitigation:**

* Refresh token rotation ensures reused tokens are invalid
* Access tokens are short-lived

**Result:**

* Replay attempts fail after first use

---

### 3. Token Tampering

**Scenario:**
Client modifies JWT payload (email/role)

**Mitigation:**

* JWT is signed using HMAC
* `JwtService.parse()` validates signature

**Result:**

* Any tampering → token rejected

---

## JWT vs Session-Based Authentication

| Feature     | JWT (Current System) | Session-Based         |
| ----------- | -------------------- | --------------------- |
| Storage     | Client-side          | Server-side           |
| Scalability | High                 | Limited               |
| DB Calls    | Minimal              | Frequent              |
| Revocation  | Manual (tokens)      | Easy (session delete) |
| Stateless   | Yes                  | No                    |

### Why JWT here?

* Works well for **microservices / distributed systems**
* Reduces server load
* Enables **stateless scaling**

---

## Full System Interaction Diagram

```
          ┌───────────────┐
          │   User Service│
          │ (Auth + User) │
          └──────┬────────┘
                 │
     ┌───────────┼───────────┐
     │                       │
     ▼                       ▼
┌─────────────┐      ┌─────────────┐
│ Item Service│      │Booking Serv.│
│ (Search)    │◄────►│ (Bookings)  │
└──────┬──────┘      └──────┬──────┘
       │                    │
       └──────────┬─────────┘
                  ▼
           PostgreSQL DB
```

### Interaction Summary

* **User Service**:

    * Handles authentication & identity
    * Issues tokens used by other services

* **Item Service**:

    * Provides searchable inventory
    * Depends on booking data for availability

* **Booking Service**:

    * Manages reservations
    * Ensures no overlaps

* All services:

    * Share database schemas
    * Use user identity via JWT

---

## Summary

The system is designed with a strong focus on:

* Security (token rotation, validation)
* Scalability (stateless auth)
* Consistency (DB constraints in bookings)
* Performance (indexed search queries)

It follows modern backend architecture principles suitable for production systems.
