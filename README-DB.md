# Database Documentation

## Overview

This database is designed to support a booking platform with users, items, bookings, and authentication/token management. The schema is divided into multiple logical schemas for better organization and separation of concerns.

Schemas used:

* `user_schema`
* `item_schema`
* `booking_schema`
* `token_schema`

---

## 1. User Schema (`user_schema`)

### Table: `users`

Stores all registered users of the platform.

| Column        | Type           | Description                      |
| ------------- | -------------- | -------------------------------- |
| id            | BIGSERIAL (PK) | Unique user identifier           |
| email         | VARCHAR(255)   | Unique email address             |
| password      | VARCHAR(255)   | Encrypted password               |
| role          | VARCHAR(50)    | User role (e.g., USER, ADMIN)    |
| name          | VARCHAR(255)   | User's name                      |
| mobile_number | VARCHAR(20)    | Contact number                   |
| is_verified   | BOOLEAN        | Email verification status        |
| trust_status  | VARCHAR(255)   | Trust level (default: UNTRUSTED) |
| created_at    | TIMESTAMP      | Record creation time             |
| updated_at    | TIMESTAMP      | Last update time                 |

---

## 2. Item Schema (`item_schema`)

### Table: `items`

Represents items available for booking/renting.

| Column         | Type                   | Description             |
| -------------- | ---------------------- | ----------------------- |
| id             | BIGSERIAL (PK)         | Unique item ID          |
| owner_id       | BIGINT                 | Owner (User ID)         |
| category_id    | BIGINT                 | Category reference      |
| title          | VARCHAR(255)           | Item title              |
| description    | TEXT                   | Item description        |
| price_per_day  | DOUBLE PRECISION       | Rental cost per day     |
| deposit_amount | DOUBLE PRECISION       | Security deposit        |
| status         | VARCHAR(50)            | Availability status     |
| available_from | DATE                   | Start of availability   |
| available_to   | DATE                   | End of availability     |
| location       | geography(Point, 4326) | Geo location (PostGIS)  |
| search_vector  | tsvector               | Full-text search vector |
| created_at     | TIMESTAMP              | Creation timestamp      |
| updated_at     | TIMESTAMP              | Last updated timestamp  |

### Indexes & Features

* **Location Index (GIST):** Enables fast geospatial queries
* **Search Index (GIN):** Supports full-text search on title & description

### Trigger: Search Vector

Automatically updates `search_vector` before insert/update using:

```
to_tsvector('english', title + description)
```

---

## 3. Booking Schema (`booking_schema`)

### Table: `bookings`

Handles booking transactions between renters and items.

| Column     | Type             | Description                               |
| ---------- | ---------------- | ----------------------------------------- |
| id         | BIGSERIAL (PK)   | Booking ID                                |
| item_id    | BIGINT           | Item being booked                         |
| renter_id  | BIGINT           | User who booked                           |
| start_date | DATE             | Booking start                             |
| end_date   | DATE             | Booking end                               |
| amount     | DOUBLE PRECISION | Total price                               |
| status     | VARCHAR(50)      | Booking status (PENDING, CONFIRMED, etc.) |
| created_at | TIMESTAMP        | Created timestamp                         |
| updated_at | TIMESTAMP        | Updated timestamp                         |

### Constraint: No Overlapping Bookings

A **GIST exclusion constraint** prevents overlapping bookings for the same item:

* Applies only when status is `PENDING` or `CONFIRMED`
* Uses PostgreSQL `daterange`

This ensures:

* No double booking
* Strong consistency at DB level

---

## 4. Token Schema (`token_schema`)

Handles authentication-related tokens.

### 4.1 Refresh Tokens

#### Table: `refresh_tokens`

| Column     | Type           | Description       |
| ---------- | -------------- | ----------------- |
| id         | BIGSERIAL (PK) | Token ID          |
| user_id    | BIGINT         | Associated user   |
| token      | VARCHAR(255)   | Unique token      |
| expiry_at  | TIMESTAMP      | Expiration time   |
| is_revoked | BOOLEAN        | Revocation flag   |
| created_at | TIMESTAMP      | Created timestamp |

**Index:** `user_id`

**Behavior:**

* Tokens are **rotated on refresh**
* Old tokens are deleted or revoked

---

### 4.2 Email Verification Tokens

#### Table: `email_verification_tokens`

| Column     | Type           | Description       |
| ---------- | -------------- | ----------------- |
| id         | BIGSERIAL (PK) | Token ID          |
| user_id    | BIGINT         | Associated user   |
| token      | VARCHAR(255)   | Unique token      |
| expires_at | TIMESTAMP      | Expiration time   |
| created_at | TIMESTAMP      | Created timestamp |
| updated_at | TIMESTAMP      | Updated timestamp |

---

### 4.3 Password Reset Tokens

#### Table: `password_reset_tokens`

| Column      | Type           | Description       |
| ----------- | -------------- | ----------------- |
| id          | BIGSERIAL (PK) | Token ID          |
| user_id     | BIGINT         | Associated user   |
| token       | VARCHAR(255)   | Unique token      |
| expiry_date | TIMESTAMP      | Expiration time   |
| created_at  | TIMESTAMP      | Created timestamp |

---

## Relationships

* `users` → `items` (owner_id)
* `users` → `bookings` (renter_id)
* `users` → `refresh_tokens`, `email_verification_tokens`, `password_reset_tokens`

All token tables use:

```
ON DELETE CASCADE
```

Meaning:

* Deleting a user removes all related tokens

---

## Key Design Decisions

### 1. Schema Separation

Each domain (user, item, booking, token) is isolated to keep the system modular and scalable.

### 2. Refresh Token Rotation

Improves security by:

* Preventing reuse of stolen tokens
* Ensuring single valid refresh token per session

### 3. Database-Level Constraints

Critical rules like **no overlapping bookings** are enforced at DB level instead of relying only on application logic.

### 4. Full-Text Search Support

Efficient searching using PostgreSQL `tsvector` + GIN index.

### 5. Geospatial Queries

PostGIS integration allows location-based filtering (e.g., nearby items).

---

## Flow Summary

### Booking Flow

1. User creates item
2. Another user books item
3. DB ensures no overlap
4. Booking stored with status

### Auth Flow

1. User logs in → gets access + refresh token
2. Access expires → refresh endpoint called
3. Old refresh token revoked
4. New tokens issued

---

## Notes for Developers

* Always update timestamps manually or via application logic
* Ensure refresh token replacement is handled on frontend
* Use GIST index carefully (requires extension enabled)
* PostGIS must be installed for location column

---

## Extensions Used

* `btree_gist` → for exclusion constraints
* `postgis` → for geolocation

---

## Table-wise Overview (Quick Reference)

This section gives a quick, practical view of what each table is responsible for.

---

### 1. `user_schema.users`

**Purpose:** Core user identity and account management

* Stores login credentials (email, password)
* Tracks user role (USER / ADMIN)
* Maintains verification status
* Tracks trust level (`UNTRUSTED`, etc.)

👉 Used in:

* Authentication
* Authorization
* Ownership of items
* Token relationships

---

### 2. `item_schema.items`

**Purpose:** Represents rentable/listable items

* Owned by a user (`owner_id`)
* Contains pricing + availability window
* Supports:

    * 🌍 Geo search (PostGIS location)
    * 🔍 Full-text search (`search_vector`)

👉 Used in:

* Search APIs (geo + keyword)
* Booking creation

---

### 3. `booking_schema.bookings`

**Purpose:** Handles reservations between users and items

* Links renter → item
* Stores booking date range
* Maintains booking status (PENDING, CONFIRMED, etc.)

🔥 Key Feature:

* Prevents overlapping bookings using DB constraint

👉 Used in:

* Availability checks
* Booking lifecycle

---

### 4. `token_schema.refresh_tokens`

**Purpose:** Session management (long-lived auth)

* Stores refresh tokens per user
* Supports token rotation
* Tracks expiry + revocation

👉 Used in:

* Refresh access token flow

---

### 5. `token_schema.email_verification_tokens`

**Purpose:** Email verification flow

* One-time token per user
* Used to verify account after signup

👉 Used in:

* Account activation

---

### 6. `token_schema.password_reset_tokens`

**Purpose:** Password reset flow

* Temporary token for resetting password
* Short-lived and one-time use

👉 Used in:

* Forgot password feature

---

## High-Level Flow Mapping

### 🧑 User Lifecycle

1. User registers → entry in `users`
2. Email verification token created
3. User verifies → `is_verified = true`

---

### 🔐 Authentication Flow

1. Login → generate access + refresh token
2. Store refresh token in `refresh_tokens`
3. On expiry → rotate refresh token

---

### 📦 Item Lifecycle

1. User creates item → stored in `items`
2. Item becomes searchable via:

    * location
    * keyword

---

### 📅 Booking Flow

1. User searches items
2. Query filters:

    * location
    * keyword
    * availability
3. User books item → insert into `bookings`
4. DB prevents overlaps automatically

---

## Final Thoughts

This schema is designed with a strong focus on:

* Data integrity
* Security
* Scalability
* Query performance

It is production-ready and follows modern backend best practices.
