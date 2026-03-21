# Item Service Documentation (Advanced)

## Overview

The Item Service manages creation, lifecycle, and discovery of items that can be rented. It provides:

* Ownership-guarded mutations (activate/deactivate/price/availability)
* Geo + keyword search with ranking
* Availability-aware filtering using booking data

This service is optimized for **read-heavy search** with strong **data integrity** for writes.

---

## Architecture (High-Level)

```
Client
  │
  ▼
ItemController
  │
  ▼
ItemService  ───────────────► TrustGateService
  │
  ├────────► ItemRepository (JPA + Native SQL)
  │
  └────────► BookingRepository (availability checks)
  │
  ▼
PostgreSQL (PostGIS + Full-Text Search)
```

---

## API Documentation

### 1. Create Item

**POST** `/items`

**Request**

```
{
  "categoryId": Long,
  "title": String,
  "description": String,
  "pricePerDay": Double,
  "depositAmount": Double,
  "availableFrom": "YYYY-MM-DD",
  "availableTo": "YYYY-MM-DD",
  "longitude": Double,
  "latitude": Double
}
```

**Notes**

* Requires authenticated & trusted user
* Location stored as PostGIS `geography(Point, 4326)`

---

### 2. Activate / Deactivate

**PUT** `/items/{id}/activate`
**PUT** `/items/{id}/deactivate`

**Rules**

* Only owner can mutate
* Only trusted users allowed

---

### 3. Update Price

**PUT** `/items/{id}/updatePrice?price=VALUE`

**Rules**

* price > 0
* owner-only mutation

---

### 4. Update Availability

**PUT** `/items/{id}/updateAvailability?from=DATE&to=DATE`

**Rules**

* Cannot shrink availability if active bookings exist

---

### 5. Search Items

**GET** `/items/search`

**Params**

* lat, lon
* radiusKm
* startDate, endDate
* keyword (optional)
* limit, offset

**Returns**

* itemId, ownerId, title, description
* pricePerDay, distance (km), textScore

---

## Core Flows

### Create Item Flow

```
Request → Controller → Service
  → Trust check
  → Domain creation
  → DB save
  → Response (itemId)
```

### Update Availability Flow

```
Request → Controller → Service
  → Ownership validation
  → Fetch bookings (PENDING, CONFIRMED)
  → Check shrink conflict
  → Update DB
```

### Search Flow (End-to-End)

```
Client
  │
  ▼
Controller (/items/search)
  │
  ▼
Service (convert radius km → meters)
  │
  ▼
Repository (native SQL)
  │
  ▼
PostgreSQL
  ├─ Geo filter (GIST)
  ├─ Text filter (GIN)
  ├─ Booking exclusion (NOT EXISTS)
  ├─ Ranking
  └─ Pagination
  │
  ▼
DTO Mapping → Response
```

---

## Search Query Deep Dive

The native query combines multiple concerns in one pass.

### 1. Geo Filtering (PostGIS)

* `ST_DWithin(location, user_point, radius)`
* Uses **GIST index**
* Reduces candidate set early

### 2. Full-Text Search

* `search_vector @@ plainto_tsquery(keyword)`
* Uses **GIN index**

### 3. Availability Filtering

```
NOT EXISTS (
  SELECT 1 FROM bookings
  WHERE overlapping dates
)
```

* Eliminates already-booked items

### 4. Ranking Formula

```
(textScore * 0.7) + ((1 / (1 + distance)) * 0.3)
```

* Balances relevance vs proximity

### 5. Pagination

* `LIMIT + OFFSET`
* Simple but not ideal for large offsets

---

## Query Execution Mental Model

```
Step 1: Filter ACTIVE items
Step 2: Apply geo radius (GIST)
Step 3: Apply keyword match (GIN)
Step 4: Remove overlapping bookings
Step 5: Compute score
Step 6: Sort + paginate
```

---

## Indexing Strategy

### Required Indexes

1. Geo Index

```
GIST(location)
```

2. Full-text Index

```
GIN(search_vector)
```

3. Partial Index (Optimization)

```
GIST(location) WHERE status = 'ACTIVE'
```

### Why Partial Index?

* Eliminates inactive rows early
* Reduces scan size significantly

---

## Performance Considerations

### Strengths

* Early filtering via geo + status
* Indexed full-text search
* Single query execution

### Trade-offs

* GIN + GIST cannot be fully combined
* OFFSET pagination slows at scale

### Improvements (Future)

* Keyset pagination
* Pre-filter geo → then text
* Caching hot search results

---

## Business Rules

* Only ACTIVE items appear in search
* Only trusted users can mutate
* Only owners can modify items
* Availability cannot conflict with bookings

---

## Edge Cases

* Empty keyword → skip text filtering
* Large radius → heavy scan (mitigated by index)
* Concurrent availability updates vs bookings

---

## Design Decisions

### 1. PostGIS over plain lat/lon

* Enables efficient spatial queries

### 2. Full-text search inside DB

* Avoids external search systems (Elasticsearch)

### 3. Single query for search

* Reduces round trips
* Keeps ranking consistent

### 4. Booking-aware filtering

* Ensures only actually available items are shown

---

## Summary

The Item Service is designed as a **search-optimized service** combining:

* Geo-spatial querying
* Full-text relevance
* Availability constraints

It balances correctness and performance by pushing heavy logic into the database while keeping business rules enforced at the service layer.
