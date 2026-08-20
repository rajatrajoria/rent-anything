package com.rajat.rent_anything.item.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Repository responsible for item persistence and search operations.
 *
 * Provides:
 * - Standard CRUD operations for items.
 * - Geospatial item search.
 * - Full-text search.
 * - Availability-aware item discovery.
 *
 * Search functionality combines:
 * - Location proximity (PostGIS)
 * - Keyword relevance (PostgreSQL Full Text Search)
 * - Availability filtering
 * - Ranking and pagination
 */
@Repository
public interface ItemRepository extends JpaRepository<ItemEntity, Long> {

    /**
     * Retrieves an item by its identifier.
     *
     * @param itemId item identifier
     * @return matching item if found
     */
    Optional<ItemEntity> findById(Long itemId);

    /**
     * Persists an item entity.
     *
     * @param itemEntity item to persist
     * @return saved entity
     */
    ItemEntity save(ItemEntity itemEntity);

    /**
     * Retrieves all items owned by the given user.
     *
     * @param ownerId owner identifier
     * @return items ordered from most to least recently created
     */
    List<ItemEntity> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);

    /**
     * Searches for available items within a specified geographic radius
     * and optionally matches them against a keyword query.
     *
     * Search Features:
     * - Geospatial filtering using PostGIS.
     * - Full-text search using PostgreSQL search vectors.
     * - Booking conflict detection.
     * - Distance-based ranking.
     * - Text relevance ranking.
     * - Keyset (seek) pagination.
     *
     * Filtering Logic:
     * 1. Item must be ACTIVE.
     * 2. Item must be within the requested radius.
     * 3. Item must match the keyword (if provided).
     * 4. Item must not have conflicting bookings.
     *
     * Ranking Formula:
     *
     * Final Score =
     *      (Text Relevance * 70%)
     *      +
     *      (Distance Score * 30%)
     *
     * This prioritizes search relevance while still favoring
     * nearby items.
     *
     * Availability Check:
     * Items are excluded when they contain PENDING or CONFIRMED
     * bookings overlapping the requested rental period.
     *
     * Pagination:
     * Results are ordered by (score DESC, itemId DESC) — itemId breaks
     * ties deterministically, since two items can share the same score.
     * The first page is requested with afterScore/afterItemId left null;
     * each subsequent page is requested using the score and itemId of the
     * last row from the previous page. This avoids the performance cliff
     * of large OFFSET values, since Postgres can seek directly to the
     * cursor position instead of scanning and discarding every prior row.
     *
     * @param latitude search latitude
     * @param longitude search longitude
     * @param radiusMeters search radius in meters
     * @param startDate desired rental start date
     * @param endDate desired rental end date
     * @param keyword optional keyword filter
     * @param limit maximum results to return
     * @param afterScore score of the last item on the previous page, or null for the first page
     * @param afterItemId id of the last item on the previous page, or null for the first page
     * @return ranked list of matching items
     */
    @Query(value = """
    SELECT * FROM (
        SELECT
        i.id AS itemId,
        i.owner_id AS ownerId,
        i.title AS title,
        i.description AS description,
        i.price_per_day AS pricePerDay,

        -- Text relevance score generated using PostgreSQL Full Text Search.
        COALESCE(
            ts_rank(i.search_vector, plainto_tsquery(:keyword)),
            0
        ) AS textScore,

        -- Distance between item location and search location (meters).
        ST_Distance(
            i.location,
            ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)
        ) AS distance,

        -- Hybrid ranking based on relevance and proximity.
        (
            COALESCE(ts_rank(i.search_vector, plainto_tsquery(:keyword)), 0) * 0.7
        )
        +
        (
            (1 / (1 + ST_Distance(i.location, ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)))) * 0.3
        ) AS score

    FROM item_schema.items i

    WHERE i.status = 'ACTIVE'

        -- Restrict results to the requested search radius.
        AND ST_DWithin(
            i.location,
            ST_SetSRID(ST_MakePoint(:lon, :lat), 4326),
            :radiusMeters
        )

        -- Optional full-text keyword filter.
        AND (
            :keyword IS NULL
            OR i.search_vector @@ plainto_tsquery(:keyword)
        )

        -- Exclude items already reserved during the requested period.
        AND NOT EXISTS (
            SELECT 1
            FROM booking_schema.bookings b
            WHERE b.item_id = i.id
              AND b.status IN ('PENDING','CONFIRMED')
              AND b.start_date <= :endDate
              AND b.end_date >= :startDate
        )
    ) t

    -- Keyset filter: only rows strictly after the previous pages cursor.
    WHERE (
        CAST(:afterScore AS DOUBLE PRECISION) IS NULL
        OR t.score < CAST(:afterScore AS DOUBLE PRECISION)
        OR (t.score = CAST(:afterScore AS DOUBLE PRECISION) AND t.itemId < CAST(:afterItemId AS BIGINT))
    )

    ORDER BY t.score DESC, t.itemId DESC

    LIMIT :limit
    """, nativeQuery = true)
    List<ItemSearchRow> searchAvailableItemsWithinRadiusAndWithKeywords(
            @Param("lat") double latitude,
            @Param("lon") double longitude,
            @Param("radiusMeters") double radiusMeters,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("keyword") String keyword,
            @Param("limit") int limit,
            @Param("afterScore") Double afterScore,
            @Param("afterItemId") Long afterItemId
    );
}