# Rent Anything — Engineering Handbook

A peer-to-peer rental marketplace API — list anything, discover it nearby, and book it for a date range, with trust and consistency enforced at the database, not just the app.

**Stack:** Spring Boot (Java) · PostgreSQL + PostGIS · Flyway migrations · S3-compatible object storage (MinIO locally, Backblaze B2/Cloudflare R2 in the cloud) · JWT, stateless auth

This is a handoff/reference document. For deep dives on a single module, see `README-BOOKING.md`, `README-DB.md`, `README-ITEM.md`, `README-KYC.md`, `README-SECURITY.md`, `README-USER.md`.

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
  - [KYC controller](#kyc-controller--kyc)
  - [Admin KYC controller](#admin-kyc-controller--adminkyc)
- [Business rules — the load-bearing ones](#business-rules--the-load-bearing-ones)
- [Data model](#data-model)
- [Error handling](#error-handling)
- [User scenarios](#user-scenarios)
- [Edge & failure scenarios](#edge--failure-scenarios)
- [Known gaps & things to watch](#known-gaps--things-to-watch)
- [Repo map](#repo-map)

---

## Overview

Rent Anything is a Spring Boot monolith organized as five bounded contexts — **user**, **item**, **booking**, **kyc**, **notification** — each with its own domain object, JPA entity, repository, service, and controller. There is no frontend in this repository; a companion Next.js app (`rent-anything-frontend`) sits on top of this API.

The core loop: a user signs up, verifies their email, and gets **trusted** — originally a blind admin toggle, now driven by a real KYC review (submit identity details + an ID document + a selfie, an admin reviews the evidence, approving auto-flips trust; the manual toggle still exists as an admin escape hatch). Once trusted, they can list items with photos and an availability window. Any visitor — logged in or not — can search listings by location, radius, dates, and keyword. A trusted renter books a window on an item; the owner confirms or the renter cancels; background schedulers expire stale requests and complete finished rentals automatically.

Three things are worth internalizing before touching this codebase: **trust gating** is checked in the service layer on almost every write path (not just at signup), **booking-conflict safety** is enforced twice — once as an app-level pre-check for fast feedback, and once as a Postgres exclusion constraint that is the actual source of truth under concurrency — and **object storage is provider-agnostic**: the same MinIO-SDK-based client code talks to local MinIO in dev and a real S3-compatible bucket (B2, R2, or AWS S3 itself) in any other environment, purely via env vars.

## Architecture

Every authenticated request takes the same path before it reaches business logic:

```
Client → CORS filter → JwtAuthenticationFilter → SecurityContext → Controller → Service → Repository → PostgreSQL
```

Five contexts, each owning its slice end to end:

| Context | Owns |
|---|---|
| **user** | Identity, JWT + refresh tokens, email verification, password reset, trust status, admin actions. |
| **item** | Listings: creation, activation, pricing, availability, geo + full-text search, image uploads to object storage. |
| **booking** | Reservation lifecycle as a state machine, conflict detection, expiration and completion schedulers. |
| **kyc** | Identity verification submissions, admin review, and the trust-status auto-flip that follows a decision. |
| **notification** | Outbound email (Gmail SMTP) for verification links, password resets, and account links to the frontend. |

Item and booking are the most coupled: search excludes items with conflicting bookings via a `NOT EXISTS` subquery, and creating a booking reads the item's availability window and active status directly. `kyc` is coupled to `user` in one direction only — it calls `UserService.setTrustStatus(...)` to flip trust as a side effect of a review decision, but nothing in `user` knows `kyc` exists.

## Stack & configuration

Key values from `application.yaml` — the numbers that shape how the system behaves in practice:

| Setting | Value | Effect |
|---|---|---|
| `jwt.access-token.expiration` | 3,600,000 ms | Access tokens are valid 1 hour. `JwtService` reads this key exactly — no default fallback, so a typo here fails startup loudly instead of silently using a hardcoded value (see [Known gaps](#known-gaps--things-to-watch)). |
| `jwt.refresh-token.expiration-days` | 7 | Refresh tokens live 7 days, rotated on every use. |
| `jwt.email.verification.expiration-minutes` | 5 | Verification links expire fast — resend flow exists for a reason. |
| `jwt.password.reset.expiration-minutes` | 15 | Reset link window. |
| `app.cors.allowed-origins` | `${CORS_ALLOWED_ORIGINS:http://localhost:3000}` | Comma-separated list of origins the frontend is served from. Required — Spring Security ignores CORS entirely unless explicitly enabled, which cost real debugging time before it was added. |
| `app.frontend-url` | `${FRONTEND_URL:http://localhost:3000}` | Base URL used to build verification/reset links in emails. Previously hardcoded to `http://localhost:8080` (the *backend's* own address) — every email sent from anywhere but local dev pointed users at a dead link. Fixed. |
| `booking.expiration.timeout-minutes` | 10 | A PENDING booking not confirmed within 10 minutes is auto-expired. |
| `booking.expiration.fixed-rate` | 60,000 ms | Expiration scheduler tick. |
| `booking.completion.fixed-rate` | 60,000 ms | Completion scheduler tick. |
| `spring.servlet.multipart.max-file-size` | 10 MB | Matches the per-image cap enforced in `ItemImageService` and `KycService`. |
| `spring.jpa.hibernate.ddl-auto` | `validate` | Schema is Flyway-owned; Hibernate never auto-migrates. |
| `minio.endpoint` / `minio.region` / `minio.access-key` / `minio.secret-key` / `minio.bucket-name` | all env-var driven, default to local MinIO (`localhost:9000`, `us-east-1`, `minioadmin`/`minioadmin`, `rent-anything`) | Every field needed to point the storage client at *any* S3-compatible provider. Nothing here is MinIO-specific — the `io.minio` dependency is just a generic S3 SDK. Swapping to Backblaze B2 or Cloudflare R2 is a config-only change: new endpoint, region (`auto` for R2, B2's region code for B2), and credentials. See `README-DB.md` and `README-SECURITY.md` for the access-control model around presigned URLs. |

Secrets (`NEONDB_URL`, `JWT_SECRET_KEY`, `EMAIL_USERNAME`/`PASSWORD`, `MINIO_*`) are injected via environment variables — see `docker-compose.yaml` for local Postgres/MinIO defaults, though in practice this project runs against a managed Neon Postgres even locally, with `docker-compose`'s Postgres service unused. Database is Postgres with the `postgis` and `btree_gist` extensions enabled; every schema change lives as a numbered Flyway migration under `src/main/resources/db/migration` (currently `V1`–`V16`).

## Domain model

### Entities at a glance

| Entity | Key fields | Notes |
|---|---|---|
| **User** | id, email, password, role, name, mobileNumber, isVerified, trustStatus | One row per account. `role` and `trustStatus` are independent axes — see below. `trustStatus` is now exposed on `GET /users/me` (previously it wasn't, so the frontend could only react to a failed trust-gated call rather than show current status). |
| **Item** | id, ownerId, categoryId, title, description, pricePerDay, depositAmount, status, availableFrom/To, location (geography), search_vector | Owned by a user; location is PostGIS `geography(Point,4326)`; `search_vector` is trigger-maintained. |
| **ItemImage** | id, itemId, imageKey, isThumbnail, displayOrder | Up to 5 per item; first upload becomes thumbnail automatically. Publicly viewable by design (see [Security](#business-rules--the-load-bearing-ones)). |
| **Booking** | id, itemId, renterId, startDate, endDate, amount, status | A state-machine object (see below); amount = inclusive days × pricePerDay. |
| **KycSubmission** | id, userId (unique), legalName, dateOfBirth, address (line1/2, city, state, postalCode, country), idDocumentType, idDocumentImageKey, selfieImageKey, status, rejectionReason, reviewedBy, reviewedAt | One row per user, upserted on resubmission (not a history table). Never publicly accessible — presigned URLs for its two documents are only ever generated for the submitting user or an admin. |

### Enums that gate behavior

| Enum | Values | Where it matters |
|---|---|---|
| `UserRole` | USER, ADMIN | Maps to `ROLE_USER` / `ROLE_ADMIN`; gates `/admin/**` and `/admin/kyc/**` only. |
| `TrustStatus` | PENDING, TRUSTED, UNTRUSTED | Gates almost every write in item and booking services, regardless of role. New users start untrusted/pending and become TRUSTED either via an admin manually flipping the status (`PATCH /admin/{userId}/trust-status`) or, now the intended primary path, via KYC approval. |
| `ItemStatus` | ACTIVE, INACTIVE, DELETED | Only ACTIVE items are searchable or bookable. `DELETED` is defined but no endpoint sets it today. |
| `BookingStatus` | PENDING, CONFIRMED, CANCELLED, COMPLETED, EXPIRED | Driven by a State-pattern object (`PendingState`, `ConfirmedState`, …) — invalid transitions throw `InvalidStateActionException`. |
| `KycStatus` | PENDING, APPROVED, REJECTED | Own state machine on `KycSubmission`, orthogonal to `TrustStatus` but drives it: approve → `TRUSTED`, reject → `UNTRUSTED`. Enforced in `KycSubmission.approve()`/`reject()`/`resubmit()` — a submission can only be reviewed once (PENDING → APPROVED/REJECTED) and only resubmitted while REJECTED. |
| `IdDocumentType` | PASSPORT, DRIVERS_LICENSE, NATIONAL_ID, OTHER | Strict enum on the KYC submission, with `OTHER` as a deliberate catch-all rather than free text. |

### Booking state machine

```
PENDING → CONFIRMED → COMPLETED
PENDING → CANCELLED
PENDING → EXPIRED        (10 min no confirmation, scheduler-driven)
CONFIRMED → CANCELLED
```

CONFIRMED → COMPLETED is scheduler-driven once `endDate` has passed. CANCELLED, COMPLETED, and EXPIRED are terminal — no state class allows leaving them.

### KYC state machine

```
(no submission) → PENDING            (first submit)
PENDING → APPROVED                   (admin approve; User.trustStatus → TRUSTED)
PENDING → REJECTED                   (admin reject + required reason; User.trustStatus → UNTRUSTED)
REJECTED → PENDING                   (resubmit, overwrites the row in place)
```

APPROVED is terminal today — there's no endpoint to re-review an approved submission or revoke trust through this path (only the manual admin override can do that). PENDING and APPROVED both block resubmission (`KYC_007`).

## Auth model

Stateless JWT auth: no server-side sessions, every request re-authenticates via a signed bearer token. `JwtAuthenticationFilter` runs before Spring's default login filter, reads `Authorization: Bearer …`, validates the signature, and populates the `SecurityContext` with a `CustomUserDetails` built from the token's email + role claims. `SecurityConfig` also registers a `CorsConfigurationSource` bean (origins from `app.cors.allowed-origins`) — Spring Security ignores CORS entirely unless a config source is both defined *and* wired into the filter chain via `.cors(...)`, which is easy to forget and previously wasn't done at all, silently blocking every cross-origin browser request.

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
| everything else | Authenticated. `/admin/**` and `/admin/kyc/**` additionally require `ROLE_ADMIN` via class-level `@PreAuthorize("hasRole('ADMIN')")` — not a `SecurityConfig` matcher, so a new admin controller must remember to add the annotation itself. |

> **Two separate gates.** **Role** (USER/ADMIN) only controls `/admin/**` and `/admin/kyc/**`. **Trust** (`TrustGateService`) controls almost everything else that mutates item or booking state — creating an item, activating it, changing price/availability, and creating a booking all call `trustGateService.ensureUserIsTrusted(userId)` first, independent of role. A brand-new, fully verified USER cannot list or book anything until trusted — today, normally by submitting KYC and getting approved.

> **KYC documents are never public.** Unlike item images, `KycDocumentStorageService` never hands out a presigned URL from a `permitAll()` route — only `GET /kyc/me` (the submitting user) and the `/admin/kyc/**` endpoints (ROLE_ADMIN) can ever see one. This is enforced entirely in application code, not by object-storage bucket policy — see `README-SECURITY.md`.

## API reference

### Auth controller — `/auth`

Fully public · entry point into the account system.

| Method | Path | Params | What happens |
|---|---|---|---|
| POST | `/auth/signup` | email, password | Creates the user, generates an email-verification token, emails a verification link (`{frontend-url}/verify-email?token=...`). Returns the new user id. |
| POST | `/auth/login` | `{email, password}` | Authenticates via Spring's `AuthenticationManager`, returns an access token + refresh token. Fails with 401 if unverified (`DisabledException`) or credentials are wrong. |
| POST | `/auth/refresh` | refreshToken | Validates + rotates the refresh token, issues a new access + refresh token pair. |
| GET | `/auth/verify-email` | token | Marks the account verified; token is single-use. |
| POST | `/auth/resend-verification-email` | email | Re-sends a verification link if the account exists and isn't yet verified. Always returns success — doesn't leak whether the email is registered. |
| POST | `/auth/forgot-password` | email | Issues a reset token and emails a reset link (`{frontend-url}/reset-password?token=...`). Same non-enumeration behavior as resend-verification. |
| POST | `/auth/reset-password` | token, newPassword | Validates the token, updates the password, deletes the token. |

### User controller — `/users`

Requires authentication · acts on the caller's own account.

| Method | Path | Params | What happens |
|---|---|---|---|
| GET | `/users/me` | — | Returns the caller's profile: id, email, name, mobileNumber, isVerified, role, timestamps, **trustStatus**. |
| PUT | `/users/password` | `{currentPassword, newPassword}` | Validates the current password before swapping it. In-app change (not the forgot-password flow). |
| POST | `/users/logout` | refreshToken | Revokes one refresh token — ends this session's ability to renew access tokens. |
| POST | `/users/logoutAll` | — | Revokes every refresh token for the user. Use for "log out everywhere" or after a password change. |

### Admin controller — `/admin`

Requires `ROLE_ADMIN` · everything here is privileged.

| Method | Path | Params | What happens |
|---|---|---|---|
| PATCH | `/admin/{userId}/trust-status` | `{status: TRUSTED\|UNTRUSTED\|PENDING}` | Manually sets a user's trust status. Predates KYC and remains a deliberate escape hatch — no self-service or automatic promotion path exists through this endpoint, and it has no relationship to any `KycSubmission` row (it doesn't touch one). |
| GET | `/admin/users` | trustStatus?, verified?, page=0, size=20 | Lists users, most recently created first — the moderation queue. `?trustStatus=PENDING` is the main use case: finding who needs a trust review without touching the database directly. |

There's still no endpoint to see trust-change *history* — an admin can find who's pending review, but the audit trail for a manual override is just server logs. (The KYC path is slightly better here — see [README-KYC.md](README-KYC.md) — but it's also not a full history, just the current row.)

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
| GET | `/items/mine` | authenticated | — | Lists all items owned by the caller (any status), most recently created first, each with its thumbnail if one exists — powers the dashboard's "My listings" tab. |

**Search ranking.** The native SQL query filters by `ST_DWithin` (GIST-indexed geo radius) and `plainto_tsquery` (GIN-indexed keyword match, skipped when keyword is blank), excludes items with an overlapping booking via `NOT EXISTS`, then ranks:

```
score = (textScore × 0.7) + ((1 / (1 + distanceKm)) × 0.3)
```

**Pagination is keyset (seek-based), not offset-based.** Rows are ordered by `(score DESC, itemId DESC)` — `itemId` is a deterministic tiebreaker for the (fairly common) case of two items sharing a score. Each row in the response includes its own `score`; to fetch the next page, pass the last row's `score` and `itemId` back as `afterScore`/`afterItemId`. Omit both for the first page. This keeps every page O(limit) to fetch — Postgres seeks straight to the cursor position instead of scanning and discarding everything before an `OFFSET`.

### Item image controller — `/items`

Image upload/read/delete for a listing, backed by object storage.

| Method | Path | Access | Params | What happens |
|---|---|---|---|---|
| POST | `/items/{itemId}/images` | owner | files: multipart[] | Uploads 1+ images. Every file in the batch is validated before any is uploaded — one bad file fails the whole request, nothing partial gets persisted. First image ever uploaded becomes the thumbnail automatically. |
| GET | `/items/{itemId}/images` | public | — | All images, ordered by display order. |
| GET | `/items/{itemId}/thumbnail` | public | — | Just the thumbnail (or null). |
| DELETE | `/items/images/{imageId}` | owner | — | Deletes from storage + DB. If the deleted image was the thumbnail, the next remaining image (by display order) is promoted. |

**Limits:** max 5 images per item · max 10 MB each · JPEG, PNG, or WEBP only. Objects live under key `items/{itemId}/{uuid}.{ext}` in the configured bucket (`minio.bucket-name`); URLs handed to clients are 1-hour presigned GETs, generated on demand, never stored. This route is intentionally `permitAll()` for GETs — item photos are meant to be public.

### Booking controller — `/api/bookings`

Requires authentication · note the differing URL prefix from every other controller (see Known gaps).

| Method | Path | Access | Params | What happens |
|---|---|---|---|---|
| POST | `/api/bookings/create` | trusted | `{itemId, startDate, endDate}` | Caller becomes the renter. Validates dates fall inside item availability, checks for conflicts, creates a PENDING booking. Amount = inclusive days × pricePerDay. |
| POST | `/api/bookings/{id}/confirm` | owner | — | Item owner accepts the request. PENDING → CONFIRMED. |
| POST | `/api/bookings/{id}/cancel` | renter or owner | — | Either party can cancel a PENDING or CONFIRMED booking. |
| GET | `/api/bookings/mine` | authenticated | — | Lists bookings the caller made as a renter, most recent first, with item title/owner joined in. Powers the dashboard's "My bookings" tab. |
| GET | `/api/bookings/received` | authenticated | — | Lists bookings made against items the caller owns, most recent first. Powers "Booking requests." Implemented as a subquery on `item_schema.items` filtered by `ownerId` — there's no FK between `bookings.item_id` and `items.id`, so this is a genuine join, not a cascade lookup. |

### KYC controller — `/kyc`

Requires authentication only — no trust or role gate, since this endpoint's entire purpose is to be the path an untrusted user takes toward becoming trusted.

| Method | Path | Params | What happens |
|---|---|---|---|
| POST | `/kyc/submit` | multipart: legalName, dateOfBirth, addressLine1, addressLine2?, city, state, postalCode, country (2-letter ISO), idDocumentType, `idDocument` file, `selfie` file | Creates a new submission (PENDING) if the caller has none, or resubmits in place if their existing one is REJECTED (`KYC_007` if PENDING/APPROVED). Validates both files (≤10MB, JPEG/PNG/WEBP) before uploading either; on resubmission, new documents are uploaded and the row saved *before* the old documents are deleted from storage, so a failed resubmission can't leave a user with no documents on file. |
| GET | `/kyc/me` | — | Returns the caller's own submission (status, the details they submitted, rejection reason if any), or `data: null` if they haven't submitted yet — that's a normal state, not an error. |

### Admin KYC controller — `/admin/kyc`

Requires `ROLE_ADMIN`, same `@PreAuthorize` convention as `/admin`.

| Method | Path | Params | What happens |
|---|---|---|---|
| GET | `/admin/kyc` | status?, page=0, size=20 | Lists submissions, oldest-PENDING-first by default — the review queue. Each row includes the submitting user's email (looked up per-row; fine at this scale, see `README-KYC.md`). |
| GET | `/admin/kyc/{id}` | — | Full detail including two 1-hour presigned URLs (ID document, selfie), generated on demand. |
| PATCH | `/admin/kyc/{id}/approve` | — | Sets the submission APPROVED and, in the same transaction, calls `UserService.setTrustStatus(userId, TRUSTED)`. Fails with `KYC_006` if the submission isn't PENDING. |
| PATCH | `/admin/kyc/{id}/reject` | `{reason}` (required) | Sets the submission REJECTED with the reason and, in the same transaction, calls `UserService.setTrustStatus(userId, UNTRUSTED)`. Same `KYC_006` guard. |

See [README-KYC.md](README-KYC.md) for the full data model, storage/security design, and the trust-flip transaction boundary in detail.

## Business rules — the load-bearing ones

**Trust gate.** Checked before: creating an item, activating/deactivating an item, updating price, updating availability, creating a booking. Not checked on: confirming or cancelling a booking (only ownership/renter identity matters there), browsing, image management (only ownership), submitting KYC (the whole point is to let an untrusted user reach trust).

**Trust is now KYC-driven by default, with a manual override.** Approving a KYC submission sets `TRUSTED`; rejecting sets `UNTRUSTED`. Both happen atomically with the submission's own status change (one `@Transactional` method, one DB round trip conceptually). The pre-existing `PATCH /admin/{userId}/trust-status` still works independently and doesn't touch KYC state at all — an admin can always hand-flip a user regardless of what their `KycSubmission` row says.

**Ownership.** Every item mutation and image mutation checks `item.ownerId == callerId` in the service layer — there's no endpoint-level owner check, so a new mutation added to `ItemController` without a matching check in `ItemService` would silently be exploitable.

**Booking conflict safety.** Two layers: an app-level query (`findConflictingBookings`, overlap = `startDate ≤ reqEnd AND endDate ≥ reqStart`) rejects obvious conflicts before insert; a Postgres **GIST exclusion constraint** using `btree_gist` on `daterange` (scoped to PENDING/CONFIRMED status) is the real guarantee — a `DataIntegrityViolationException` from a losing race is caught and turned into the same `BookingConflictException` the pre-check throws.

**Self-booking.** Enforced in the `Booking.create()` domain factory: `renterId.equals(itemOwnerId)` throws before the row is ever built.

**Shrinking availability.** An owner narrowing `availableFrom`/`availableTo` is blocked if any PENDING or CONFIRMED booking would fall outside the new window — protects renters who already have a live reservation.

**Activation requires photos.** An item needs **at least 2 images** uploaded before it can be activated (`ItemService.activateItem`) — a business rule enforced only at activation time, not at creation.

**KYC review is one-shot.** `KycSubmission.approve()`/`reject()` both throw `KYC_006` if the submission isn't currently PENDING — an admin can't approve an already-approved row or flip a decision by calling the other endpoint. To change a decision, the user must resubmit (only possible from REJECTED) and go through review again.

**KYC documents are never public.** Their storage keys live under a `kyc/{userId}/{kind}/{uuid}.{ext}` prefix in the *same* bucket as item photos, but presigned URLs for them are only ever generated from `GET /kyc/me` or the `/admin/kyc/**` endpoints — never a `permitAll()` route. Access control here is 100% application code, not bucket policy (see `README-SECURITY.md`).

## Data model

Five Postgres schemas, one Flyway migration chain (`V1` → `V16`):

| Schema.table | Purpose | Notable feature |
|---|---|---|
| `user_schema.users` | Accounts: credentials, role, verification, trust. | `trust_status` added in V13; now flippable by both the manual admin endpoint and KYC review. |
| `item_schema.items` | Listings: pricing, availability, geo, search text. | GIST index on `location`; GIN index + trigger-maintained `tsvector` on title+description; partial `GIST(location) WHERE status='ACTIVE'` index added in V14. |
| `item_schema.item_images` | Uploaded photos, thumbnail flag, order. | Binary lives in object storage; DB stores the key. FK to `items(id)` `ON DELETE CASCADE` (V15). |
| `booking_schema.bookings` | Reservations: item, renter, dates, amount, status. | GIST exclusion constraint on `daterange`, scoped to PENDING/CONFIRMED — the DB physically refuses an overlapping insert. `item_id`/`renter_id` are plain `BIGINT`, **not** FK-enforced — deleting a user or item does not cascade-delete their bookings, and any cleanup script must delete bookings explicitly first. |
| `kyc_schema.kyc_submissions` | One-row-per-user identity verification submissions. | FK to `users(id)` `ON DELETE CASCADE` (V16) — deleting a user does clean this one up automatically, unlike bookings/items. Unique constraint on `user_id` is what makes "one row, upsert on resubmit" possible. |
| `token_schema.refresh_tokens` | Long-lived session tokens. | Indexed by `user_id`; rotated on each refresh. |
| `token_schema.email_verification_tokens` | Signup verification. | One-time, expiry-based. |
| `token_schema.password_reset_tokens` | Forgot-password flow. | One active token per user. |

All token tables *and* `kyc_submissions` cascade-delete when their owning user is deleted. `items` and `bookings` do **not** — `owner_id`/`renter_id`/`item_id` are bare `BIGINT` columns throughout, a deliberate (if inconsistent) looseness that predates this handbook. Extensions required: `postgis`, `btree_gist`. See `README-DB.md` for full column-level detail and the exact FK/cascade behavior of every table.

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

`GlobalExceptionHandler` maps every domain exception (`UserException`, `BookingException`, `ItemException`, `KycException`, and their subclasses) to its `ErrorCode`, plus validation errors, oversized uploads, bad login, and a catch-all 500. Adding a new bounded context's exception family means exactly one new `@ExceptionHandler` block here — `kyc` followed this pattern exactly, no other changes needed. A representative slice of the error catalog:

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
| KYC_001 | 404 | KYC submission not found |
| KYC_004 | 400 | KYC document exceeds max size |
| KYC_005 | 400 | Unsupported KYC document file type |
| KYC_006 | 409 | KYC submission already reviewed |
| KYC_007 | 409 | KYC resubmission not allowed (still PENDING/APPROVED) |
| KYC_008 | 500 | KYC document storage operation failed |

Full list in `common/enums/ErrorCode.java`.

## User scenarios

### Sign up and get verified — *new user*

1. `POST /auth/signup` with email + password. Account is created unverified, trust status defaults to UNTRUSTED.
2. A 5-minute verification link is emailed via Gmail SMTP, pointing at `{app.frontend-url}/verify-email?token=...`.
3. User clicks it → the frontend page calls `GET /auth/verify-email?token=…` → account marked verified.
4. If the link expired, `POST /auth/resend-verification-email` issues a new one — silently no-ops if the account is already verified or doesn't exist.
5. `POST /auth/login` now succeeds, returning an access + refresh token.

**Result:** Authenticated but still **untrusted** — can browse and search, cannot list or book anything yet.

### Get verified — *newly signed-up user, the common path*

1. From their account page, the user fills out a KYC form: legal name, date of birth, address, an ID document type + photo, and a selfie. `POST /kyc/submit`.
2. Submission sits `PENDING`. `GET /kyc/me` reflects this so the frontend can show a "your verification is under review" state proactively.
3. An admin reviews it via `GET /admin/kyc?status=PENDING`, inspects both documents via the presigned URLs in `GET /admin/kyc/{id}`, and either:
   - `PATCH /admin/kyc/{id}/approve` → submission APPROVED, `trustStatus` flips to TRUSTED in the same transaction.
   - `PATCH /admin/kyc/{id}/reject` with a required reason → submission REJECTED, `trustStatus` flips to UNTRUSTED, the user sees the reason and a resubmit form.
4. If rejected, the user can `POST /kyc/submit` again — same endpoint, it detects the existing REJECTED row and overwrites it in place, returning to PENDING.

**Result:** TRUSTED with real evidence behind it, no separate manual admin step required beyond the review decision itself.

### List an item for rent — *trusted owner*

1. The owner is TRUSTED (via KYC or the manual override) — otherwise every step past this one 403s with `USR_005`.
2. `POST /items` with category, title, description, price, deposit, availability window, lat/lon. Item is created as **INACTIVE** and not yet searchable.
3. `POST /items/{id}/images` with 2–5 photos (JPEG/PNG/WEBP, ≤10MB each). The first upload is auto-flagged as the thumbnail.
4. `PUT /items/{id}/activate`. If fewer than 2 images exist, this throws — the owner has to go back to step 3.

**Result:** Item is ACTIVE and now appears in `/items/search` results within its availability window and geo radius. `GET /items/mine` shows it (and every other status) on the owner's own dashboard.

### Search for and book an item — *trusted renter*

1. `GET /items/search` — no login required. Filter by lat/lon/radiusKm/dates, optional keyword; results are geo + text ranked and already exclude items booked over those dates.
2. `GET /items/{itemId}` for full detail — photos, price, deposit.
3. Must be logged in and trusted to go further. `POST /api/bookings/create` with itemId + date range.
4. Server checks: dates inside the item's availability window, no conflicting PENDING/CONFIRMED booking, renter ≠ owner, item is ACTIVE. Booking is created as **PENDING**.
5. Owner reviews (`GET /api/bookings/received` for their queue) and either `POST /api/bookings/{id}/confirm`s it, or it sits unconfirmed.
6. If unconfirmed for 10 minutes, `BookingExpirationScheduler` flips it to **EXPIRED** automatically — no action needed from either side.
7. Once `endDate` passes on a CONFIRMED booking, `BookingCompletionScheduler` flips it to **COMPLETED**.

**Result:** A completed rental, with the date range now free again once the booking leaves PENDING/CONFIRMED status. `GET /api/bookings/mine` shows the renter's own history at any point in this flow.

### Owner manages a live listing — *trusted owner*

1. Price changes any time via `PUT /items/{id}/updatePrice` — takes effect immediately for new bookings; existing bookings keep their locked-in `amount`.
2. Availability changes via `PUT /items/{id}/updateAvailability` — narrowing the window is rejected if it would strand an existing PENDING/CONFIRMED booking outside the new range.
3. Pull a listing off the market anytime with `PUT /items/{id}/deactivate` — hides it from search, blocks new bookings, doesn't touch bookings already in flight.
4. Swap photos via `DELETE /items/images/{imageId}` + a fresh `POST /items/{id}/images`; deleting the thumbnail auto-promotes the next image.

**Result:** Listing reflects current terms without disrupting renters already mid-booking.

### Forgot password — *any registered user*

1. `POST /auth/forgot-password` with email. Always returns success — an attacker probing emails learns nothing either way.
2. If the account exists, a 15-minute reset token is emailed, pointing at `{app.frontend-url}/reset-password?token=...`.
3. `POST /auth/reset-password` with token + new password. Token is deleted on use, and all of the account's refresh tokens are revoked as part of the same password change.

### Suspicious activity / lost device — *logged-in user*

1. From a trusted device, call `POST /users/logoutAll` — revokes every refresh token on the account.
2. Every other device's session dies the moment its 1-hour access token expires and it tries to refresh.
3. Optionally follow with `PUT /users/password` to lock the attacker out entirely (requires the current password, so this only works if the attacker doesn't have it too).

**Result:** All sessions except the current access token (still live for up to an hour) are cut off from renewing.

### Admin reviews a new user — *ROLE_ADMIN*

Two paths exist today:

- **KYC review (intended default):** `GET /admin/kyc?status=PENDING` surfaces submissions waiting on review, oldest first. `PATCH /admin/kyc/{id}/approve` or `.../reject` decides it and flips trust automatically.
- **Manual override (escape hatch, predates KYC):** `GET /admin/users?trustStatus=PENDING` surfaces everyone waiting on review by user record (independent of whether they've ever submitted KYC). `PATCH /admin/{userId}/trust-status` with `{"status": "TRUSTED"}` flips it directly, no evidence required.

**Result:** User can now create items and bookings. Setting status back to UNTRUSTED at any time immediately blocks those actions again — existing listings/bookings are untouched.

## Edge & failure scenarios

**Two renters race for the same dates.** Both pass the app-level conflict pre-check simultaneously (classic TOCTOU window). Both attempt to insert a booking row. Postgres's GIST exclusion constraint lets exactly one `INSERT` through; the second raises a constraint violation, which `BookingService.createBooking` catches and rethrows as `BookingConflictException` (409). No double-booking is possible, even under a true race — the guarantee lives in the database, not the application.

**Untrusted user tries to list or book.** Any call to create/activate/deactivate/reprice/reschedule an item, or create a booking, first calls `TrustGateService.ensureUserIsTrusted`. Fails with `403 TRUST_GATE_FAILURE`. No path around this short of KYC approval or an admin manual override.

**Owner tries to book their own item.** `Booking.create()` checks `renterId.equals(itemOwnerId)` before the domain object is even constructed → 400 `SELF_BOOKING_NOT_ALLOWED`, enforced in the domain layer so it can't be bypassed by hitting the service a different way.

**Owner tries to shrink availability under a live booking.** `updateAvailability` loads all PENDING/CONFIRMED bookings for the item; if any would fall outside the proposed new window, the whole update is rejected, not partially applied → 400 `INVALID_AVAILABILITY_WINDOW`.

**6th image upload, or a 4K raw photo.** `existingImageCount + files.size() > 5` is rejected before any upload to storage happens. Every file in the batch is validated (size, type, non-empty) before any file is uploaded — a single oversized or wrong-type file fails the whole request with nothing written to storage or the DB.

**User tries to resubmit KYC while PENDING or APPROVED.** `KycSubmission.resubmit()` throws `KYC_007` before any new files are even uploaded — same pattern as trust gating: fail fast, before doing any work that would need to be undone.

**Admin tries to review an already-reviewed submission.** `approve()`/`reject()` both check `status == PENDING` first, throwing `KYC_006` — protects against a double-click or two admin tabs racing to decide the same submission (whichever request's transaction commits first wins; the second sees the now-non-PENDING row and fails cleanly rather than silently double-processing).

**A KYC resubmission's upload fails partway.** `KycService.submit()` uploads the *new* documents and saves the row before deleting the *old* ones — so a failure between "new docs uploaded" and "row saved" leaves orphaned new objects in storage (a minor leak) rather than a user with zero documents on file. The reverse ordering was deliberately avoided.

## Known gaps & things to watch

> Fixed since the previous pass through this codebase, in case the "why" is useful later: **no CORS policy existed at all** (every cross-origin browser request was silently blocked — added a `CorsConfigurationSource` bean + `.cors(...)` wiring), **`jwt.access-token.expiration` had a mismatched property key** (`JwtService` read `jwt.access.token.expiration`, which doesn't exist in `application.yaml`; a hardcoded default of 3600000 masked it entirely — fixed the key, and removed the default so a future mismatch fails loudly at startup instead of silently), and **verification/reset email links pointed at the backend's own address** (`http://localhost:8080/...`) instead of the frontend — routed through the new `app.frontend-url` config instead.

- **Bucket auto-provisioning doesn't exist.** Neither `MinioImageStorageService` nor `MinioKycDocumentStorageService` ever calls `bucketExists`/`makeBucket` — the configured bucket must already exist before the app is used, or every upload 500s. This bit hard the first time local MinIO was stood up standalone (no Docker) with a fresh, empty data directory. Worth adding a startup check.
- **No FK enforcement between `bookings` and `items`/`users`.** `item_id`/`renter_id` are bare `BIGINT`s. Deleting a user or item does not cascade-delete their bookings — any manual cleanup (e.g. wiping test data) must delete bookings *before* items/users, in that order, or it'll leave orphaned rows. `kyc_submissions` is the one table that *does* cascade properly (FK added in V16).
- **`ItemStatus.DELETED` is unused.** The enum value exists; no service path sets it, so there's currently no soft-delete for items.
- **Access tokens can't be revoked early.** Logout only revokes refresh tokens; a stolen access token stays valid for up to an hour with no server-side kill switch (standard JWT tradeoff, but worth knowing before promising "instant" logout).
- **Inconsistent route prefixing.** Booking lives under `/api/bookings` while everything else (`/items`, `/users`, `/auth`, `/admin`, `/kyc`, `/admin/kyc`) has no `/api` prefix. Cosmetic today, but worth normalizing before it's baked into more clients.
- **KYC is one-row-per-user, not a history table.** A rejected-then-resubmitted-then-approved user has no record of the rejected attempt anywhere except server logs. Fine for a small side project with one admin; would need a lightweight append-only audit table if that history ever matters (e.g. a user disputing a rejection).
- **No resubmission cooldown on KYC.** Any REJECTED submission is immediately resubmittable — not a moderation-abuse vector at current scale, but worth revisiting if this ever sees adversarial traffic.
- **Every `ROLE_ADMIN` sees every KYC submission's full PII** (legal name, DOB, address, both document images) with no further scoping or per-admin audit log of who viewed what. Fine for a single-admin setup; would need real access logging with a second admin.
- **No payment integration.** `Booking.amount` is calculated and stored, but nothing charges, escrows, or refunds it — deposit handling is a stored number with no enforcement.
- **Config keys that don't match `application.yaml` fail silently by default.** `@Value("...")` does exact-key resolution with no compile-time check against the yaml — this class of bug has now bitten this codebase twice (`RefreshTokenService`/`PasswordResetService` previously, `JwtService`'s access-token expiration this pass). Worth a quick grep (`@Value("...:default")` next to a yaml path) whenever new config is added, since a default value is exactly what lets the mismatch hide.

## Repo map

| Path | What's there |
|---|---|
| `src/main/java/.../user/` | Accounts, admin, and — inside `application/TrustGateService.java` — the trust check used everywhere else. `UserService.setTrustStatus` is the seam `kyc` uses to flip trust. |
| `src/main/java/.../security/` | JWT filter/service, refresh/verification/reset token services, `SecurityConfig` (auth rules + CORS). |
| `src/main/java/.../item/` | Listings, search, `ItemImageController`/`ItemImageService` for storage-backed photos, and `application/MinioImageStorageService` (the item-image storage impl). |
| `src/main/java/.../booking/domain/state/` | The State-pattern classes — start here to understand or extend the booking lifecycle. |
| `src/main/java/.../booking/application/*Scheduler.java` | Expiration and completion background jobs. |
| `src/main/java/.../kyc/` | New bounded context: `domain/KycSubmission.java` (state transitions), `application/KycService.java` (user-facing submit/view), `application/KycAdminService.java` (review, and the trust-flip transaction), `application/MinioKycDocumentStorageService.java` (its own storage impl, separate from item images'). See `README-KYC.md`. |
| `src/main/java/.../common/` | `ErrorCode`, `ApiResponse`/`ApiError` envelope, `GlobalExceptionHandler`. |
| `src/main/resources/db/migration/` | V1–V16 Flyway migrations — the schema's actual history. `V16` created `kyc_schema.kyc_submissions`. |
| `README-*.md` | Deep-dive docs per module (item, booking, kyc, user/security, DB) — this handbook is the map; those are the territory. |

---

*This is a living document, not an auto-generated one — update it by hand when behavior here drifts from the code.*
