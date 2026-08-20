# KYC Service Documentation

## Overview

The KYC (know-your-customer) service replaces a blind admin toggle with evidence-backed trust: a user submits identity details plus an ID document and a selfie, an admin reviews the actual documents, and the review decision automatically flips the user's trust status — no separate manual step. It is a new bounded context (`kyc/`) added alongside `user`, `item`, and `booking`, mirroring `user`'s domain/infrastructure/application/api package split rather than the flatter pattern `item`'s image sub-feature uses, since this data is more sensitive.

The pre-existing manual admin override (`PATCH /admin/{userId}/trust-status`) is untouched and remains available — KYC is a new *path* to trust, not a replacement for the escape hatch.

---

## Architecture (High-Level)

```
Client
  │
  ▼
KycController (/kyc)              AdminKycController (/admin/kyc)
  │                                  │
  ▼                                  ▼
KycService ──────────────►  KycAdminService ───────► UserService.setTrustStatus
  │                                  │
  ├──────────► KycSubmissionRepository
  │
  └──────────► KycDocumentStorageService ──────► S3-compatible object storage
```

`KycAdminService.approve()`/`.reject()` are the only things that ever call `UserService.setTrustStatus(...)` from outside the `user` package — that's the entire coupling between the two contexts, and it's one-directional.

---

## API Documentation

### 1. Submit KYC

**POST** `/kyc/submit`

Multipart request — text fields plus two files, not JSON.

#### Fields

```
legalName: String
dateOfBirth: LocalDate (YYYY-MM-DD)
addressLine1: String
addressLine2: String (optional)
city: String
state: String
postalCode: String
country: String (2-letter ISO code)
idDocumentType: PASSPORT | DRIVERS_LICENSE | NATIONAL_ID | OTHER
idDocument: file (JPEG/PNG/WEBP, ≤10MB)
selfie: file (JPEG/PNG/WEBP, ≤10MB)
```

#### Authentication

* Requires authenticated user only — no trust or role gate

#### Behavior

* No existing submission → creates a new one, status PENDING
* Existing submission is REJECTED → overwrites it in place, status back to PENDING
* Existing submission is PENDING or APPROVED → rejected with `KYC_007`

#### Response

```
{
  "success": true,
  "data": { KycSubmissionResponseDto }
}
```

---

### 2. Get My Submission

**GET** `/kyc/me`

#### Response

```
{
  "success": true,
  "data": { KycSubmissionResponseDto } | null
}
```

`null` means the caller hasn't submitted yet — a normal state, not an error, so the frontend never has to special-case a 404 here.

---

### 3. Admin: List Submissions

**GET** `/admin/kyc?status=PENDING&page=0&size=20`

* Requires `ROLE_ADMIN`
* `status` optional — omit to list all
* Ordered oldest-PENDING-first so the queue works through arrival order
* Response shape matches `GET /admin/users`: `{submissions, page, size, totalElements, totalPages}`

---

### 4. Admin: Get Submission Detail

**GET** `/admin/kyc/{id}`

* Requires `ROLE_ADMIN`
* Returns full submitted detail plus two **1-hour presigned URLs** (`idDocumentImageUrl`, `selfieImageUrl`), generated on demand — never stored, never cached.

---

### 5. Admin: Approve

**PATCH** `/admin/kyc/{id}/approve`

* Requires `ROLE_ADMIN`
* Fails with `KYC_006` if the submission isn't currently PENDING
* On success: submission → APPROVED, `reviewedBy`/`reviewedAt` set, and — same transaction — `User.trustStatus` → TRUSTED

---

### 6. Admin: Reject

**PATCH** `/admin/kyc/{id}/reject`

```
{ "reason": String }   // required, @NotBlank
```

* Requires `ROLE_ADMIN`
* Same `KYC_006` guard as approve
* On success: submission → REJECTED with the reason, and — same transaction — `User.trustStatus` → UNTRUSTED

---

## Core Flows

### Submit Flow (first time)

```
Request → Controller → Service
  → Validate text fields (non-blank, DOB present, 2-letter country)
  → Validate both files (size, content-type) before uploading either
  → Upload idDocument → storage key
  → Upload selfie → storage key
  → KycSubmission.submitNew(...) → status PENDING
  → Save
  → Response
```

### Resubmit Flow

```
Request → Controller → Service
  → Validate (same as above)
  → Load existing KycSubmission, capture OLD storage keys
  → Upload NEW documents (new keys)
  → submission.resubmit(...) — throws KYC_007 if not REJECTED
  → Save row with new keys
  → Delete OLD documents from storage (only after save succeeds)
  → Response
```

The upload-then-save-then-delete-old ordering is deliberate: a failure partway through never leaves a user with zero documents on file, at the cost of a possible orphaned object in storage if the delete step itself fails (logged, not retried — see Edge Cases).

### Review Flow (approve)

```
Request → AdminKycController → KycAdminService.approve()  [@Transactional]
  → requireAdmin(adminId)
  → Load submission
  → submission.approve(adminId)   — throws KYC_006 if not PENDING
  → Save submission (status APPROVED)
  → userService.setTrustStatus(submission.userId, TRUSTED)
  → (transaction commits — both changes land together or neither does)
```

Reject is identical in shape, ending in `setTrustStatus(..., UNTRUSTED)` instead.

---

## Domain Model

`KycSubmission` is a plain domain object (no JPA annotations) with three static/instance factories that *are* the state machine — none of these rules live in the service layer:

* `submitNew(...)` — brand-new row, status PENDING.
* `resubmit(...)` — instance method; throws `KYC_007` unless `status == REJECTED`, then overwrites fields and resets to PENDING (clearing `reviewedBy`/`reviewedAt`/`rejectionReason`).
* `approve(adminId)` / `reject(adminId, reason)` — instance methods; both throw `KYC_006` unless `status == PENDING`.

This mirrors how `Item.activate()` and `Booking.create()` embody their own invariants elsewhere in the codebase, rather than trusting every call site to re-check the rule.

### Fields

| Field | Type | Notes |
|---|---|---|
| id | Long | PK |
| userId | Long | Unique — one row per user |
| legalName | String | Free text; may differ from the account's display `name` |
| dateOfBirth | LocalDate | |
| addressLine1/2, city, state, postalCode, country | String | `country` validated as exactly 2 characters (ISO-3166), not further checked against a real code list |
| idDocumentType | IdDocumentType | PASSPORT / DRIVERS_LICENSE / NATIONAL_ID / OTHER |
| idDocumentImageKey, selfieImageKey | String | Storage keys, not URLs — URLs are generated on demand |
| status | KycStatus | PENDING / APPROVED / REJECTED |
| rejectionReason | String? | Required input on reject, null otherwise |
| reviewedBy | Long? | Admin's user id |
| reviewedAt | LocalDateTime? | |
| createdAt, updatedAt | LocalDateTime | `createdAt` is the *first* submission time even across resubmissions — `updatedAt` moves, `createdAt` doesn't |

---

## Storage Design

`KycDocumentStorageService` is a **new interface**, not a reuse of item images' `ImageStorageService` — that interface's `upload(Long itemId, ...)` signature and internal key format are item-specific, and retrofitting it for KYC would mean misusing `itemId` to mean `userId`. `MinioKycDocumentStorageService` implements the new interface but reuses the same singleton `MinioClient` bean (`item.config.MinioConfiguration`) — that bean is a plain root-context Spring bean, not scoped to the `item` package, so no duplication was needed there.

**Key format:** `kyc/{userId}/{kind}/{uuid}.{ext}`, where `kind` is `"idDocument"` or `"selfie"`.

**Bucket:** the *same* bucket as item images (`minio.bucket-name`, defaults `rent-anything`), not a separate one. This was a deliberate call after checking: neither this app nor its infra provisions any bucket-level public-read policy — item photos are "public" purely because the app hands out presigned URLs from a `permitAll()` route, not because of anything at the bucket level. Since KYC documents never go through a public route, there's no actual security difference between a separate bucket and a shared one with a distinct key prefix; a separate bucket would only add a second manual provisioning step per environment with no real access-control benefit. See `README-SECURITY.md` for the full reasoning.

**Presigned URL expiry:** 1 hour, same as item images — generated fresh on every `GET /admin/kyc/{id}` call, never persisted.

---

## Business Rules

* Both documents required and validated (size, content-type) before either is uploaded — an invalid selfie can't leave an orphaned ID-document upload behind.
* Resubmission only from REJECTED; PENDING and APPROVED are locked (`KYC_007`).
* Review only from PENDING; can't re-decide an already-decided submission (`KYC_006`).
* Rejection reason is mandatory — there's no way to reject silently and leave the user with nothing to act on.
* Approve/reject and the trust-status flip happen in one transaction — they cannot drift out of sync (no "submission says APPROVED but user is still UNTRUSTED" state is reachable).
* Any `ROLE_ADMIN` can review any submission — no additional per-admin scoping exists today.

---

## Edge Cases

* **User resubmits, then the storage delete of the old documents fails.** The new submission still saved successfully and the user is unblocked; the old objects are simply orphaned in storage (logged as a warning, not retried). A minor storage leak, not a correctness problem.
* **Two admin tabs try to review the same submission.** Whichever transaction commits first wins; the second sees a non-PENDING row and fails cleanly with `KYC_006` rather than double-processing or racing on the trust-status write.
* **User's account gets deleted.** `kyc_submissions` has an `ON DELETE CASCADE` FK to `users(id)` (added in V16) — the row disappears automatically. The storage *objects* do not — nothing currently cleans those up. A known gap, not a bug, at this project's scale.
* **Admin rejects with only whitespace as the reason.** Blocked server-side — `RejectKycRequest.reason` is `@NotBlank`.

---

## Design Decisions

### 1. One row per user, not a history table

Simpler schema, simpler queries (`findByUserId` instead of "latest row per user"), and no ambiguity about what `reviewedBy`/`reviewedAt` refer to. The tradeoff: no audit trail of a prior rejected attempt once resubmitted. Acceptable for a small side project with no compliance mandate to retain it; a lightweight append-only `kyc_review_log` table could be bolted on later without touching this table's shape, if that history ever becomes necessary.

### 2. Domain object owns its own transitions

`approve()`/`reject()`/`resubmit()` all self-guard against invalid states, rather than the service layer checking status before calling a setter. Keeps the invariant enforceable from exactly one place, matching `Item`/`Booking`'s existing pattern in this codebase.

### 3. Separate storage interface, shared bucket

See Storage Design above — new interface because the old one's signature doesn't fit; shared bucket because the actual security boundary (presigned URLs never handed out publicly) doesn't care which bucket the objects live in.

### 4. Trust flip lives in `KycAdminService`, not `UserService`

`UserService.setTrustStatus(userId, status)` is a narrow, opinion-free seam — it just sets the field and saves. It deliberately does *not* carry `AdminService.updateUserTrustStatus`'s self-demotion guard, because that rule is specific to the manual-override flow's business logic (an admin shouldn't be able to demote themselves via that endpoint) and doesn't apply here — a user can't KYC-approve themselves regardless.

---

## Summary

The KYC service turns "is this user trustworthy?" from a blind toggle into a reviewed decision backed by actual evidence, while keeping the underlying trust-gating mechanism (`TrustGateService`, checked throughout `item`/`booking`) completely unchanged — KYC is purely a new way to *arrive* at `TRUSTED`, not a new gate itself.

---

## What is NOT covered here

* The trust gate itself (`TrustGateService.ensureUserIsTrusted`) — see `README-USER.md`.
* Item/booking mutation rules that consume `trustStatus` — see `README-ITEM.md` and `README-BOOKING.md`.
* CORS, JWT, and general auth infrastructure — see `README-SECURITY.md`.
* Object storage bucket/region/presigned-URL configuration in general — see `README-SECURITY.md` and `README-DB.md`.

---

## Quick Mental Model

* Controller → binds multipart fields, delegates
* `KycSubmission` (domain) → owns every state transition and its guards
* `KycService` → user-facing submit/view, file validation, storage orchestration
* `KycAdminService` → review, and the one place trust status gets flipped by this feature
* `KycDocumentStorageService` → S3-compatible object storage, never public
