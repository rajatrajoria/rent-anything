# Rent Anything — Engineering Handbook

A peer-to-peer rental marketplace API — list anything, discover it nearby, and book it for a date range, with trust and consistency enforced at the database, not just the app.

**Stack:** Spring Boot (Java) · PostgreSQL + PostGIS · Flyway migrations · MinIO (S3-compatible) · JWT, stateless auth

This is a handoff/reference document. For deep dives on a single module, see `README-BOOKING.md`, `README-DB.md`, `README-ITEM.md`, `README-SECURITY.md`, `README-USER.md`.

---

## Table of contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Stack & configuration](#stack--configuration)
- [Domain model](#domain-model)
- [Auth model](#auth-model)
- [API reference](#api-reference)
  - [Auth controller](#auth-controller--auth)
  - [User controller](#user-controller--users)
  - [Admin controller](#admin-controller--admin)
  - [Item controller](#item-controller--items)
  - [Item image controller](#item-image-controller--items)
  - [Booking controller](#booking-controller--apibookings)
- [Business rules — the load-bearing ones](#business-rules--the-load-bearing-ones)
- [Data model](#data-model)
- [Error handling](#error-handling)
- [User scenarios](#user-scenarios)
- [Edge & failure scenarios](#edge--failure-scenarios)
- [Known gaps & things to watch](#known-gaps--things-to-watch)
- [Repo map](#repo-map)

---

## Overview

Rent Anything is a Spring Boot monolith organized as four bounded contexts — **user**, **item**, **booking**, **notification** — each with its own domain object, JPA entity, repository, service, and controller. There is no frontend in this repository; this is the API a client (web, mobile, or otherwise) would sit on top of.

The core loop: a user signs up, verifies their email, and is reviewed by an admin for **trust**. Once trusted, they can list items with photos and an availability window. Any visitor — logged in or not — can search listings by location, radius, dates, and keyword. A trusted renter books a window on an item; the owner confirms or the renter cancels; background schedulers expire stale requests and complete finished rentals automatically.

Two things are worth internalizing before touching this codebase: **trust gating** is checked in the service layer on almost every write path (not just at signup), and **booking-conflict safety** is enforced twice — once as an app-level pre-check for fast feedback, and once as a Postgres exclusion constraint that is the actual source of truth under concurrency.

## Architecture

Every authenticated request takes the same path before it reaches business logic:

```
Client → JwtAuthenticationFilter → SecurityContext → Controller → Service → Repository → PostgreSQL
```

Four contexts, each owning its slice end to end:

| Context | Owns |
|---|---|
| **user** | Identity, JWT + refresh tokens, email verification, password reset, trust status, admin actions. |
| **item** | Listings: creation, activation, pricing, availability, geo + full-text search, image uploads to MinIO. |
| **booking** | Reservation lifecycle as a state machine, conflict detection, expiration and completion schedulers. |
| **notification** | Outbound email (Gmail SMTP) for verification links and password resets. |

Item and booking are the most coupled: search excludes items with conflicting bookings via a `NOT EXISTS` subquery, and creating a booking reads the item's availability window and active status directly.

## Stack & configuration

Key values from `application.yaml` — the numbers that shape how the system behaves in practice:

| Setting | Value | Effect |
|---|---|---|
| `jwt.access-token.expiration` | 3,600,000 ms | Access tokens are valid 1 hour. |
| `jwt.refresh-token.expiration-days` | 7 | Refresh tokens live 7 days, rotated on every use. |
| `jwt.email.verification.expiration-minutes` | 5 | Verification links expire fast — resend flow exists for a reason. |
| `jwt.password.reset.expiration-minutes` | 15 | Reset link window. |
| `booking.expiration.timeout-minutes` | 10 | A PENDING booking not confirmed within 10 minutes is auto-expired. |
| `booking.expiration.fixed-rate` | 60,000 ms | Expiration scheduler tick. |
| `booking.completion.fixed-rate` | 60,000 ms | Completion scheduler tick. |
| `spring.servlet.multipart.max-file-size` | 10 MB | Matches the per-image cap enforced in `ItemImageService`. |
| `spring.jpa.hibernate.ddl-auto` | `validate` | Schema is Flyway-owned; Hibernate never auto-migrates. |

Secrets (`NEONDB_URL`, `JWT_SECRET_KEY`, `EMAIL_USERNAME`/`PASSWORD`, `MINIO_*`) are injected via environment variables — see `docker-compose.yaml` for local defaults. Database is Postgres with the `postgis` and `btree_gist` extensions enabled; every schema change lives as a numbered Flyway migration under `src/main/resources/db/migration`.

## Domain model

### Entities at a glance

| Entity | Key fields | Notes |
|---|---|---|
| **User** | id, email, password, role, name, mobileNumber, isVerified, trustStatus | One row per account. `role` and `trustStatus` are independent axes — see below. |
| **Item** | id, ownerId, categoryId, title, description, pricePerDay, depositAmount, status, availableFrom/To, location (geography), search_vector | Owned by a user; location is PostGIS `geography(Point,4326)`; `search_vector` is trigger-maintained. |
| **ItemImage** | id, itemId, imageKey, isThumbnail, displayOrder | Up to 5 per item; first upload becomes thumbnail automatically. |
| **Booking** | id, itemId, renterId, startDate, endDate, amount, status | A state-machine object (see below); amount = inclusive days × pricePerDay. |

### Enums that gate behavior

| Enum | Values | Where it matters |
|---|---|---|
| `UserRole` | USER, ADMIN | Maps to `ROLE_USER` / `ROLE_ADMIN`; gates `/admin/**` only. |
| `TrustStatus` | PENDING, TRUSTED, UNTRUSTED | Gates almost every write in item and booking services, regardless of role. New users start untrusted/pending and need an admin to flip them to TRUSTED. |
| `ItemStatus` | ACTIVE, INACTIVE, DELETED | Only ACTIVE items are searchable or bookable. `DELETED` is defined but no endpoint sets it today. |
| `BookingStatus` | PENDING, CONFIRMED, CANCELLED, COMPLETED, EXPIRED | Driven by a State-pattern object (`PendingState`, `ConfirmedState`, …) — invalid transitions throw `InvalidStateActionException`. |

### Booking state machine

```
PENDING → CONFIRMED → COMPLETED
PENDING → CANCELLED
PENDING → EXPIRED        (10 min no confirmation, scheduler-driven)
CONFIRMED → CANCELLED
```

CONFIRMED → COMPLETED is scheduler-driven once `endDate` has passed. CANCELLED, COMPLETED, and EXPIRED are terminal — no state class allows leaving them.

## Auth model

Stateless JWT auth: no server-side sessions, every request re-authenticates via a signed bearer token. `JwtAuthenticationFilter` runs before Spring's default login filter, reads `Authorization: Bearer …`, validates the signature, and populates the `SecurityContext` with a `CustomUserDetails` built from the token's email + role claims.

| Token | Lifetime | Notes |
|---|---|---|
| Access token | 1 hour | Stateless, never persisted — can't be revoked early, only left to expire. |
| Refresh token | 7 days | Stored in `token_schema.refresh_tokens`, **rotated on every use** (old one deleted/invalidated, new one issued). Revoked on logout or password change. |
| Email verification token | 5 minutes | One-time use, deleted after success. |
| Password reset token | 15 minutes | One active token per user, deleted after success. |

### Public vs. protected routes

From `SecurityConfig` — everything not listed here requires a valid access token:

| Route | Access |
|---|---|
| `/auth/**` | Public — signup, login, refresh, verify, password reset. |
| `GET /items/search` | Public — anonymous discovery. |
| `GET /items/{id}` | Public — listing detail page. |
| `GET /items/{id}/images`, `GET /items/{id}/thumbnail` | Public — listing photos. |
| everything else | Authenticated. `/admin/**` additionally requires `ROLE_ADMIN` via `@PreAuthorize`. |

> **Two separate gates.** **Role** (USER/ADMIN) only controls `/admin/**`. **Trust** (`TrustGateService`) controls almost everything else that mutates item or booking state — creating an item, activating it, changing price/availability, and creating a booking all call `trustGateService.ensureUserIsTrusted(userId)` first, independent of role. A brand-new, fully verified USER cannot list or book anything until an ADMIN sets their trust status to TRUSTED.

## API reference

### Auth controller — `/auth`

Fully public · entry point into the account system.

| Method | Path | Params | What happens |
|---|---|---|---|
| POST | `/auth/signup` | email, password | Creates the user, generates an email-verification token, emails a verification link. Returns the new user id. |
| POST | `/auth/login` | `{email, password}` | Authenticates via Spring's `AuthenticationManager`, returns an access token + refresh token. Fails with 401 if unverified (`DisabledException`) or credentials are wrong. |
| POST | `/auth/refresh` | refreshToken | Validates + rotates the refresh token, issues a new access + refresh token pair. |
| GET | `/auth/verify-email` | token | Marks the account verified; token is single-use. |
| POST | `/auth/resend-verification-email` | email | Re-sends a verification link if the account exists and isn't yet verified. Always returns success — doesn't leak whether the email is registered. |
| POST | `/auth/forgot-password` | email | Issues a reset token and emails a reset link. Same non-enumeration behavior as resend-verification. |
| POST | `/auth/reset-password` | token, newPassword | Validates the token, updates the password, deletes the token. |

### User controller — `/users`

Requires authentication · acts on the caller's own account.

| Method | Path | Params | What happens |
|---|---|---|---|
| GET | `/users/me` | — | Returns the caller's profile: id, email, name, mobileNumber, isVerified, role, timestamps. |
| PUT | `/users/password` | `{currentPassword, newPassword}` | Validates the current password before swapping it. In-app change (not the forgot-password flow). |
| POST | `/users/logout` | refreshToken | Revokes one refresh token — ends this session's ability to renew access tokens. |
| POST | `/users/logoutAll` | — | Revokes every refresh token for the user. Use for "log out everywhere" or after a password change. |

### Admin controller — `/admin`

Requires `ROLE_ADMIN` · everything here is privileged.

| Method | Path | Params | What happens |
|---|---|---|---|
| PATCH | `/admin/{userId}/trust-status` | `{status: TRUSTED\|UNTRUSTED\|PENDING}` | Sets a user's trust status. This is the only way trust ever changes — no self-service or automatic promotion path exists. |
| GET | `/admin/users` | trustStatus?, verified?, page=0, size=20 | Lists users, most recently created first — the moderation queue. `?trustStatus=PENDING` is the main use case: finding who needs a trust review without touching the database directly. |

There's still no endpoint to see trust-change *history* or flagged-account signals — an admin can find who's pending review, but not why.

### Item controller — `/items`

Mixed — search and reads are public, mutations require a trusted, authenticated owner.

| Method | Path | Access | Params | What happens |
|---|---|---|---|---|
| POST | `/items` | trusted | categoryId, title, description, pricePerDay>0, depositAmount≥0, availableFrom (≥today), availableTo, lat, lon | Creates a listing owned by the caller. Starts as **INACTIVE** — not searchable until activated. |
| PUT | `/items/{id}/activate` | owner | — | Flips status to ACTIVE. **Requires ≥2 uploaded images** or it throws. |
| PUT | `/items/{id}/deactivate` | owner | — | Hides the item from search and blocks new bookings. |
| PUT | `/items/{id}/updatePrice` | owner | price | Must be > 0. |
| PUT | `/items/{id}/updateAvailability` | owner | from, to | Rejected if any PENDING/CONFIRMED booking would fall outside the new window. |
| GET | `/items/search` | public | lat, lon, radiusKm, startDate, endDate, keyword?, limit=10, afterScore?, afterItemId? | Geo + full-text ranked search, excludes items already booked over the requested dates. Keyset-paginated — see below. |
| GET | `/items/{itemId}` | public | — | Full listing detail: pricing, availability, thumbnail, all images. |

**Search ranking.** The native SQL query filters by `ST_DWithin` (GIST-indexed geo radius) and `plainto_tsquery` (GIN-indexed keyword match, skipped when keyword is blank), excludes items with an overlapping booking via `NOT EXISTS`, then ranks:

```
score = (textScore × 0.7) + ((1 / (1 + distanceKm)) × 0.3)
```

**Pagination is keyset (seek-based), not offset-based.** Rows are ordered by `(score DESC, itemId DESC)` — `itemId` is a deterministic tiebreaker for the (fairly common) case of two items sharing a score. Each row in the response includes its own `score`; to fetch the next page, pass the last row's `score` and `itemId` back as `afterScore`/`afterItemId`. Omit both for the first page. This keeps every page O(limit) to fetch — Postgres seeks straight to the cursor position instead of scanning and discarding everything before an `OFFSET`.

### Item image controller — `/items`

Image upload/read/delete for a listing, backed by MinIO.

| Method | Path | Access | Params | What happens |
|---|---|---|---|---|
| POST | `/items/{itemId}/images` | owner | files: multipart[] | Uploads 1+ images. Every file in the batch is validated before any is uploaded — one bad file fails the whole request, nothing partial gets persisted. First image ever uploaded becomes the thumbnail automatically. |
| GET | `/items/{itemId}/images` | public | — | All images, ordered by display order. |
| GET | `/items/{itemId}/thumbnail` | public | — | Just the thumbnail (or null). |
| DELETE | `/items/images/{imageId}` | owner | — | Deletes from MinIO + DB. If the deleted image was the thumbnail, the next remaining image (by display order) is promoted. |

**Limits:** max 5 images per item · max 10 MB each · JPEG, PNG, or WEBP only.

### Booking controller — `/api/bookings`

Requires authentication · note the differing URL prefix from every other controller (see Known gaps).

| Method | Path | Access | Params | What happens |
|---|---|---|---|---|
| POST | `/api/bookings/create` | trusted | `{itemId, startDate, endDate}` | Caller becomes the renter. Validates dates fall inside item availability, checks for conflicts, creates a PENDING booking. Amount = inclusive days × pricePerDay. |
| POST | `/api/bookings/{id}/confirm` | owner | — | Item owner accepts the request. PENDING → CONFIRMED. |
| POST | `/api/bookings/{id}/cancel` | renter or owner | — | Either party can cancel a PENDING or CONFIRMED booking. |

## Business rules — the load-bearing ones

**Trust gate.** Checked before: creating an item, activating/deactivating an item, updating price, updating availability, creating a booking. Not checked on: confirming or cancelling a booking (only ownership/renter identity matters there), browsing, image management (only ownership).

**Ownership.** Every item mutation and image mutation checks `item.ownerId == callerId` in the service layer — there's no endpoint-level owner check, so a new mutation added to `ItemController` without a matching check in `ItemService` would silently be exploitable.

**Booking conflict safety.** Two layers: an app-level query (`findConflictingBookings`, overlap = `startDate ≤ reqEnd AND endDate ≥ reqStart`) rejects obvious conflicts before insert; a Postgres **GIST exclusion constraint** using `btree_gist` on `daterange` (scoped to PENDING/CONFIRMED status) is the real guarantee — a `DataIntegrityViolationException` from a losing race is caught and turned into the same `BookingConflictException` the pre-check throws.

**Self-booking.** Enforced in the `Booking.create()` domain factory: `renterId.equals(itemOwnerId)` throws before the row is ever built.

**Shrinking availability.** An owner narrowing `availableFrom`/`availableTo` is blocked if any PENDING or CONFIRMED booking would fall outside the new window — protects renters who already have a live reservation.

**Activation requires photos.** An item needs **at least 2 images** uploaded before it can be activated (`ItemService.activateItem`) — a business rule enforced only at activation time, not at creation.

## Data model

Four Postgres schemas, one Flyway migration chain (`V1` → `V15`):

| Schema.table | Purpose | Notable feature |
|---|---|---|
| `user_schema.users` | Accounts: credentials, role, verification, trust. | — |
| `item_schema.items` | Listings: pricing, availability, geo, search text. | GIST index on `location`; GIN index + trigger-maintained `tsvector` on title+description. |
| `item_schema.item_images` | Uploaded photos, thumbnail flag, order. | Binary lives in MinIO; DB stores the key. |
| `booking_schema.bookings` | Reservations: item, renter, dates, amount, status. | GIST exclusion constraint on `daterange`, scoped to PENDING/CONFIRMED — the DB physically refuses an overlapping insert. |
| `token_schema.refresh_tokens` | Long-lived session tokens. | Indexed by `user_id`; rotated on each refresh. |
| `token_schema.email_verification_tokens` | Signup verification. | One-time, expiry-based. |
| `token_schema.password_reset_tokens` | Forgot-password flow. | One active token per user. |

All token tables cascade-delete when their owning user is deleted. Extensions required: `postgis`, `btree_gist`.

## Error handling

Every response — success or failure — is wrapped the same way:

```json
{ "success": true,  "data": { ... } }
{ "success": false, "error": {
    "timeStamp": "...", "status": 404,
    "errorCode": "ITM_001", "errorName": "ITEM_NOT_FOUND",
    "message": "Item not found", "path": "/items/42"
} }
```

`GlobalExceptionHandler` maps every domain exception (`UserException`, `BookingException`, `ItemException` and their subclasses) to its `ErrorCode`, plus validation errors, oversized uploads, bad login, and a catch-all 500. A representative slice of the error catalog:

| Code | Status | Message |
|---|---|---|
| BKG_002 | 409 | Booking dates conflict |
| BKG_005 | 400 | Cannot book your own item |
| ITM_002 | 400 | Item is not active |
| ITM_007 | 400 | Invalid availability window |
| ITM_010 | 400 | Invalid number of images |
| ITM_012 | 400 | Image exceeds max size |
| ITM_013 | 404 | Item image not found |
| USR_005 | 403 | Trust gate failure |
| AUTH_001 | 401 | Account not verified |
| AUTH_002 | 401 | Invalid email or password |

Full list in `common/enums/ErrorCode.java`.

## User scenarios

### Sign up and get verified — *new user*

1. `POST /auth/signup` with email + password. Account is created unverified, trust status defaults to UNTRUSTED.
2. A 5-minute verification link is emailed via Gmail SMTP.
3. User clicks it → `GET /auth/verify-email?token=…` → account marked verified.
4. If the link expired, `POST /auth/resend-verification-email` issues a new one — silently no-ops if the account is already verified or doesn't exist.
5. `POST /auth/login` now succeeds, returning an access + refresh token.

**Result:** Authenticated but still **untrusted** — can browse and search, cannot list or book anything yet.

### List an item for rent — *trusted owner*

1. An admin has already set this user's trust status to TRUSTED — otherwise every step past this one 403s with `USR_005`.
2. `POST /items` with category, title, description, price, deposit, availability window, lat/lon. Item is created as **INACTIVE** and not yet searchable.
3. `POST /items/{id}/images` with 2–5 photos (JPEG/PNG/WEBP, ≤10MB each). The first upload is auto-flagged as the thumbnail.
4. `PUT /items/{id}/activate`. If fewer than 2 images exist, this throws — the owner has to go back to step 3.

**Result:** Item is ACTIVE and now appears in `/items/search` results within its availability window and geo radius.

### Search for and book an item — *trusted renter*

1. `GET /items/search` — no login required. Filter by lat/lon/radiusKm/dates, optional keyword; results are geo + text ranked and already exclude items booked over those dates.
2. `GET /items/{itemId}` for full detail — photos, price, deposit.
3. Must be logged in and trusted to go further. `POST /api/bookings/create` with itemId + date range.
4. Server checks: dates inside the item's availability window, no conflicting PENDING/CONFIRMED booking, renter ≠ owner, item is ACTIVE. Booking is created as **PENDING**.
5. Owner reviews and either `POST /api/bookings/{id}/confirm`s it, or it sits unconfirmed.
6. If unconfirmed for 10 minutes, `BookingExpirationScheduler` flips it to **EXPIRED** automatically — no action needed from either side.
7. Once `endDate` passes on a CONFIRMED booking, `BookingCompletionScheduler` flips it to **COMPLETED**.

**Result:** A completed rental, with the date range now free again once the booking leaves PENDING/CONFIRMED status.

### Owner manages a live listing — *trusted owner*

1. Price changes any time via `PUT /items/{id}/updatePrice` — takes effect immediately for new bookings; existing bookings keep their locked-in `amount`.
2. Availability changes via `PUT /items/{id}/updateAvailability` — narrowing the window is rejected if it would strand an existing PENDING/CONFIRMED booking outside the new range.
3. Pull a listing off the market anytime with `PUT /items/{id}/deactivate` — hides it from search, blocks new bookings, doesn't touch bookings already in flight.
4. Swap photos via `DELETE /items/images/{imageId}` + a fresh `POST /items/{id}/images`; deleting the thumbnail auto-promotes the next image.

**Result:** Listing reflects current terms without disrupting renters already mid-booking.

### Forgot password — *any registered user*

1. `POST /auth/forgot-password` with email. Always returns success — an attacker probing emails learns nothing either way.
2. If the account exists, a 15-minute reset token is emailed.
3. `POST /auth/reset-password` with token + new password. Token is deleted on use, and all of the account's refresh tokens are revoked as part of the same password change.

### Suspicious activity / lost device — *logged-in user*

1. From a trusted device, call `POST /users/logoutAll` — revokes every refresh token on the account.
2. Every other device's session dies the moment its 1-hour access token expires and it tries to refresh.
3. Optionally follow with `PUT /users/password` to lock the attacker out entirely (requires the current password, so this only works if the attacker doesn't have it too).

**Result:** All sessions except the current access token (still live for up to an hour) are cut off from renewing.

### Admin reviews a new user — *ROLE_ADMIN*

1. A new signup sits at trust status PENDING/UNTRUSTED — unable to list or book.
2. `GET /admin/users?trustStatus=PENDING` surfaces everyone waiting on review, most recent first.
3. `PATCH /admin/{userId}/trust-status` with `{"status": "TRUSTED"}`.

**Result:** User can now create items and bookings. Setting status back to UNTRUSTED at any time immediately blocks those actions again — existing listings/bookings are untouched.

## Edge & failure scenarios

**Two renters race for the same dates.** Both pass the app-level conflict pre-check simultaneously (classic TOCTOU window). Both attempt to insert a booking row. Postgres's GIST exclusion constraint lets exactly one `INSERT` through; the second raises a constraint violation, which `BookingService.createBooking` catches and rethrows as `BookingConflictException` (409). No double-booking is possible, even under a true race — the guarantee lives in the database, not the application.

**Untrusted user tries to list or book.** Any call to create/activate/deactivate/reprice/reschedule an item, or create a booking, first calls `TrustGateService.ensureUserIsTrusted`. Fails with `403 TRUST_GATE_FAILURE`. No path around this short of an admin trust update.

**Owner tries to book their own item.** `Booking.create()` checks `renterId.equals(itemOwnerId)` before the domain object is even constructed → 400 `SELF_BOOKING_NOT_ALLOWED`, enforced in the domain layer so it can't be bypassed by hitting the service a different way.

**Owner tries to shrink availability under a live booking.** `updateAvailability` loads all PENDING/CONFIRMED bookings for the item; if any would fall outside the proposed new window, the whole update is rejected, not partially applied → 400 `INVALID_AVAILABILITY_WINDOW`.

**6th image upload, or a 4K raw photo.** `existingImageCount + files.size() > 5` is rejected before any upload to MinIO happens. Every file in the batch is validated (size, type, non-empty) before any file is uploaded — a single oversized or wrong-type file fails the whole request with nothing written to MinIO or the DB.

## Known gaps & things to watch

> Fixed since the first pass through this codebase, in case the "why" is useful later: two config keys silently didn't match `application.yaml` (see below), image/activation validation now throws proper domain exceptions instead of raw `IllegalArgumentException`/`IllegalStateException`, image uploads validate the whole batch before uploading anything, `/items/search` moved from offset to keyset pagination, and `GET /admin/users` now exists.

- **Hardcoded verification/reset links.** `AuthController` builds email links as literal `http://localhost:8080/auth/...` strings — this needs to become a configurable base URL before any non-local deployment, or every verification/reset email sent from staging or prod will point users back to localhost.
- **Inconsistent route prefixing.** Booking lives under `/api/bookings` while everything else (`/items`, `/users`, `/auth`, `/admin`) has no `/api` prefix. Cosmetic today, but worth normalizing before it's baked into more clients.
- **No payment integration.** `Booking.amount` is calculated and stored, but nothing charges, escrows, or refunds it — deposit handling is a stored number with no enforcement.
- **Access tokens can't be revoked early.** Logout only revokes refresh tokens; a stolen access token stays valid for up to an hour with no server-side kill switch (standard JWT tradeoff, but worth knowing before promising "instant" logout).
- **`ItemStatus.DELETED` is unused.** The enum value exists; no service path sets it, so there's currently no soft-delete for items.
- **Config keys that don't match `application.yaml` fail silently.** `RefreshTokenService` and `PasswordResetService` both had `@Value(...)` paths that didn't line up with the yaml structure and quietly fell back to hardcoded defaults instead of erroring — now fixed for those two, but worth a quick grep for the same pattern (`@Value("...:default")` next to a yaml path) if new config gets added, since `@Value` does exact-key resolution and won't warn you.

## Repo map

| Path | What's there |
|---|---|
| `src/main/java/.../user/` | Accounts, admin, and — inside `application/TrustGateService.java` — the trust check used everywhere else. |
| `src/main/java/.../security/` | JWT filter/service, refresh/verification/reset token services, `SecurityConfig`. |
| `src/main/java/.../item/` | Listings, search, and `ItemImageController`/`ItemImageService` for MinIO-backed photos. |
| `src/main/java/.../booking/domain/state/` | The State-pattern classes — start here to understand or extend the booking lifecycle. |
| `src/main/java/.../booking/application/*Scheduler.java` | Expiration and completion background jobs. |
| `src/main/java/.../common/` | `ErrorCode`, `ApiResponse`/`ApiError` envelope, `GlobalExceptionHandler`. |
| `src/main/resources/db/migration/` | V1–V15 Flyway migrations — the schema's actual history. |
| `README-*.md` | Deep-dive docs per module (item, booking, user/security, DB) — this handbook is the map; those are the territory. |

---

*This is a living document, not an auto-generated one — update it by hand when behavior here drifts from the code.*
