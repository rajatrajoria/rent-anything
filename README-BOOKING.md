# Booking Service Documentation

## Overview

The Booking Service is responsible for managing the lifecycle of bookings in the system. It ensures that items are booked safely without conflicts, enforces business rules, and maintains consistency using both application-level checks and database-level constraints.

---

## API Documentation

### 1. Create Booking

**POST** `/api/bookings/create`

#### Request Body

```
{
  "itemId": Long,
  "startDate": "YYYY-MM-DD",
  "endDate": "YYYY-MM-DD"
}
```

#### Authentication

* Requires authenticated user
* Renter ID is derived from logged-in user

#### Response

```
{
  "success": true,
  "data": bookingId
}
```

#### Errors

* Item not found
* Invalid booking dates
* Booking conflict
* User not trusted

---

### 2. Confirm Booking

**POST** `/api/bookings/{id}/confirm`

#### Response

```
{
  "success": true
}
```

---

### 3. Cancel Booking

**POST** `/api/bookings/{id}/cancel`

#### Response

```
{
  "success": true
}
```

---

## Service Layer

### createBooking()

Flow:

1. Validate renter trust using TrustGateService
2. Fetch item
3. Validate booking dates within availability
4. Check for conflicting bookings (pre-check)
5. Create booking domain object
6. Save booking
7. Handle DB constraint violation (final safety)

Key Insight:

* Pre-check improves UX
* DB constraint ensures correctness under concurrency

---

### confirmBooking()

Flow:

1. Fetch booking
2. Convert to domain
3. Call confirm()
4. Save updated entity

---

### cancelBooking()

Flow:

1. Fetch booking
2. Convert to domain
3. Call cancel()
4. Save updated entity

---

## Repository Layer

### findConflictingBookings()

Logic:

* Finds bookings with overlapping date ranges

Condition:

```
startDate <= requestedEndDate
AND
endDate >= requestedStartDate
```

Used for early conflict detection.

---

### Scheduler Queries

1. findByStatusAndCreatedAtBefore

* Used to expire pending bookings

2. findByStatusAndEndDateBefore

* Used to mark bookings as completed

---

## Concurrency Handling

Two-layer protection:

1. Application-level pre-check
2. Database-level exclusion constraint

Final guarantee comes from DB constraint.

---

## Booking Lifecycle

States:

* PENDING
* CONFIRMED
* CANCELLED
* EXPIRED
* COMPLETED

---

## Schedulers

### BookingExpirationScheduler

* Runs periodically
* Expires bookings that remain PENDING beyond timeout

### BookingCompletionScheduler

* Runs periodically
* Marks bookings as COMPLETED after end date

---

## Key Design Decisions

1. Exclusion constraint for strong consistency
2. Pre-check for better UX
3. Transactional boundaries at service layer
4. Domain-driven design (Booking domain object)

---

## Edge Cases Handled

* Overlapping bookings
* Booking outside availability
* Concurrent booking requests
* Expired bookings
* Invalid booking ID

---

## Summary

This README strictly documents the **Booking Service**. It covers APIs, business logic, repository queries, schedulers, and concurrency handling specific to bookings only.

---

## What is NOT covered here

* Item search implementation details
* User authentication/token flows
* Other service schemas or unrelated database tables

For those, refer to their respective service READMEs.

---

## Quick Mental Model

* Controller → receives request
* Service → validates + orchestrates
* Repository → queries DB
* DB → enforces final consistency (exclusion constraint)

---

## Key Guarantees

* ❌ No overlapping bookings (DB enforced)
* ⚡ Fast feedback via pre-check
* 🔁 Lifecycle automation via schedulers
* 🔐 Only trusted users can book

---

If you extend this service, keep all booking-related logic and documentation centralized here.
