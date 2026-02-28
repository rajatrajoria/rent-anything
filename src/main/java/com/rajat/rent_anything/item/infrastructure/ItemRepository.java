package com.rajat.rent_anything.item.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ItemRepository extends JpaRepository<ItemEntity, Long> {
    Optional<ItemEntity> findById(Long itemId);
    ItemEntity save(ItemEntity itemEntity);

    @Query(value = """
    SELECT *
    FROM(
        SELECT
        i.id AS itemId,
        i.owner_id AS ownerId,
        i.title AS title,
        i.description AS description,
        i.price_per_day AS pricePerDay,

        -- Text relevance score
        COALESCE(
            ts_rank(i.search_vector, plainto_tsquery(:keyword)),
            0
        ) AS textScore,

        -- Distance from search point (in meters)
        ST_Distance(
            i.location,
            ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)
        ) AS distance

    FROM item_schema.items i

    WHERE i.status = 'ACTIVE'

        -- Geo radius filter
        AND ST_DWithin(
            i.location,
            ST_SetSRID(ST_MakePoint(:lon, :lat), 4326),
            :radiusMeters
        )

        -- Full-text filter
        AND (
            :keyword IS NULL
            OR i.search_vector @@ plainto_tsquery(:keyword)
        )

        -- Exclude already booked items
        AND NOT EXISTS (
            SELECT 1
            FROM booking_schema.bookings b
            WHERE b.item_id = i.id
              AND b.status IN ('PENDING','CONFIRMED')
              AND b.start_date <= :endDate
              AND b.end_date >= :startDate
        )
    )
    -- Ranking formula
    ORDER BY
        (textScore * 0.7)
        +
        ((1 / (1 + distance)) * 0.3)
    DESC
    LIMIT :limit OFFSET :offset
""", nativeQuery = true)
    List<ItemSearchRow> searchAvailableItemsWithinRadiusAndWithKeywords(
            @Param("lat") double latitude,
            @Param("lon") double longitude,
            @Param("radiusMeters") double radiusMeters,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("keyword") String keyword,
            @Param("limit") int limit,
            @Param("offset") int offset
    );
}
