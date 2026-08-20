# Database Documentation

## Overview

This database is designed to support a booking platform with users, items, bookings, identity verification, and authentication/token management. The schema is divided into multiple logical schemas for better organization and separation of concerns.

Schemas used:

* `user_schema`
* `item_schema`
* `booking_schema`
* `kyc_schema`
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
* **Partial Location Index (GIST, `WHERE status = 'ACTIVE'`):** Added in V14 — since search only ever looks at ACTIVE items, indexing only those rows keeps the index smaller and the geo scan cheaper than indexing every row regardless of status.
* **Partial Index (`idx_items_active`, `WHERE status = 'ACTIVE'`):** Same V14 migration, a plain btree on `id` scoped the same way.

### Trigger: Search Vector

Automatically updates `search_vector` before insert/update using:

```
to_tsvector('english', title + description)
```

---

### Table: `item_images`

Uploaded photos for a listing. Binary content lives in object storage (MinIO locally, an S3-compatible bucket like Backblaze B2 or Cloudflare R2 elsewhere) — this table only stores the storage key, never the file itself.

| Column | Type | Description |
| ------------- | -------------- | -------------------------------- |
| id | BIGSERIAL (PK) | Unique image ID |
| item_id | BIGINT, FK → `items(id)` `ON DELETE CASCADE` | Owning item |
| image_key | VARCHAR(1000) | Object storage key, e.g. `items/42/8d7a5f6c.jpg` |
| display_order | INTEGER | 1-based ordering within the item |
| is_thumbnail | BOOLEAN | Exactly one row per item should be `true`; deleting the thumbnail promotes the next by `display_order` |
| created_at | TIMESTAMP | |

Added in **V15**, alongside the FK — this is the one item-related table that *does* cascade-delete correctly (unlike `bookings`, see below). Max 5 rows per `item_id`, enforced in application code (`ItemImageService`), not by a DB constraint.

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

### No Foreign Keys on `item_id` / `renter_id`

Both are plain `BIGINT` columns — there is **no** FK to `items(id)` or `users(id)`. This means:

* Deleting an item or a user does **not** cascade-delete their bookings; they become orphaned rows referencing an id that no longer exists.
* Any cleanup script (test-data resets, account deletion) must delete `bookings` rows explicitly, and must do it *before* deleting the referenced items/users — this is the opposite of `item_images` and `kyc_submissions`, which do cascade automatically. Worth double-checking which behavior you're relying on before writing a DELETE.

---

## 4. KYC Schema (`kyc_schema`)

### Table: `kyc_submissions`

One row per user — identity-verification submissions reviewed by an admin, whose approval/rejection drives `users.trust_status`. Added in **V16**.

| Column | Type | Description |
| ---------------------- | ---------------- | ------------------------------------------------------- |
| id | BIGSERIAL (PK) | Submission ID |
| user_id | BIGINT, UNIQUE, FK → `users(id)` `ON DELETE CASCADE` | One row per user — resubmission overwrites in place |
| legal_name | VARCHAR(255) | |
| date_of_birth | DATE | |
| address_line1 | VARCHAR(255) | |
| address_line2 | VARCHAR(255), nullable | |
| city | VARCHAR(255) | |
| state | VARCHAR(255) | |
| postal_code | VARCHAR(20) | |
| country | VARCHAR(2) | ISO-3166 2-letter code, not validated against a real code list beyond length |
| id_document_type | VARCHAR(50) | `PASSPORT` / `DRIVERS_LICENSE` / `NATIONAL_ID` / `OTHER` |
| id_document_image_key | VARCHAR(1000) | Object storage key, e.g. `kyc/5/idDocument/uuid.png` |
| selfie_image_key | VARCHAR(1000) | Object storage key, e.g. `kyc/5/selfie/uuid.png` |
| status | VARCHAR(20) | `PENDING` / `APPROVED` / `REJECTED`, default `PENDING` |
| rejection_reason | VARCHAR(1000), nullable | Required input on reject, cleared on approve/resubmit |
| reviewed_by | BIGINT, nullable | Reviewing admin's user id — not FK-enforced |
| reviewed_at | TIMESTAMP, nullable | |
| created_at | TIMESTAMP | First submission time — unlike `updated_at`, unaffected by resubmission |
| updated_at | TIMESTAMP | |

**Index:** `idx_kyc_submissions_status` on `status`, since the admin review queue is always filtered by it.

### Why `user_id` is `UNIQUE`

This is what makes "one row per user, upsert on resubmit" possible at the DB level — the application can safely do `findByUserId` expecting at most one result, and a resubmission is an `UPDATE` in disguise (same row, new values) rather than an `INSERT`.

### Storage note

Documents (`id_document_image_key`, `selfie_image_key`) live in the **same bucket** as item photos, under a `kyc/{userId}/{kind}/{uuid}.{ext}` prefix — not a separate bucket. See `README-KYC.md` and `README-SECURITY.md` for why that's a deliberate choice rather than an oversight: the actual access-control boundary is application code (presigned URLs are never handed out from a public route), not bucket-level policy, so a second bucket would add provisioning overhead without a real security benefit.

---

## 5. Token Schema (`token_schema`)

Handles authentication-related tokens.

### 5.1 Refresh Tokens

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

### 5.2 Email Verification Tokens

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

### 5.3 Password Reset Tokens

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

* `users` → `items` (owner_id) — **not** FK-enforced
* `users` → `bookings` (renter_id) — **not** FK-enforced
* `items` → `item_images` (item_id) — **FK-enforced**, `ON DELETE CASCADE`
* `items` → `bookings` (item_id) — **not** FK-enforced
* `users` → `kyc_submissions` (user_id, unique) — **FK-enforced**, `ON DELETE CASCADE`
* `users` → `refresh_tokens`, `email_verification_tokens`, `password_reset_tokens` — **FK-enforced**, `ON DELETE CASCADE`

Meaning, concretely:

* Deleting a user automatically removes all their tokens and their `kyc_submissions` row.
* Deleting a user or an item does **not** automatically remove their `bookings` — those must be deleted explicitly, and first, in any manual cleanup.
* Deleting an item automatically removes its `item_images`.

This FK-enforcement is inconsistent across the schema by history, not by design — `bookings` predates the convention `item_images`/`kyc_submissions` later adopted. Worth keeping in mind before assuming a delete "just cascades."

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

### 3. `item_schema.item_images`

**Purpose:** Photos for a listing, key-only (binary lives in object storage)

* Up to 5 per item, enforced in app code
* Exactly one flagged as thumbnail
* FK to `items`, cascades on item delete

👉 Used in:

* Item activation (needs ≥2 images)
* Search result thumbnails
* Listing detail pages

---

### 4. `booking_schema.bookings`

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

### 5. `kyc_schema.kyc_submissions`

**Purpose:** Identity-verification submissions reviewed by an admin

* One row per user (`user_id` unique), overwritten on resubmission
* Drives `users.trust_status` on approve/reject
* FK to `users`, cascades on user delete

🔥 Key Feature:

* Approve/reject and the trust-status flip happen in one transaction — never out of sync

👉 Used in:

* Trust gating (the intended path to `TRUSTED`)
* Admin review queue

---

### 6. `token_schema.refresh_tokens`

**Purpose:** Session management (long-lived auth)

* Stores refresh tokens per user
* Supports token rotation
* Tracks expiry + revocation

👉 Used in:

* Refresh access token flow

---

### 7. `token_schema.email_verification_tokens`

**Purpose:** Email verification flow

* One-time token per user
* Used to verify account after signup

👉 Used in:

* Account activation

---

### 8. `token_schema.password_reset_tokens`

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

### 🪪 KYC / Trust Flow

1. User submits identity details + documents → row in `kyc_submissions` (PENDING), documents in object storage
2. Admin reviews → approves or rejects
3. Same transaction: `kyc_submissions.status` updated **and** `users.trust_status` flipped (`TRUSTED` or `UNTRUSTED`)
4. If rejected, user resubmits → same row overwritten, back to PENDING (step 2 repeats)

---

## Final Thoughts

This schema is designed with a strong focus on:

* Data integrity
* Security
* Scalability
* Query performance

It is production-ready and follows modern backend best practices.
