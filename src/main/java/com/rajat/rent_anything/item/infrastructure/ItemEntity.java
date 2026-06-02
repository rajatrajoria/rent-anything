package com.rajat.rent_anything.item.infrastructure;

import com.rajat.rent_anything.item.domain.ItemStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.locationtech.jts.geom.Point;

import java.time.LocalDate;
import java.time.LocalDateTime;
/**
 * Persistence entity representing an item listed on the platform.
 *
 * An item is a rentable asset owned by a user and made available
 * for discovery through the marketplace.
 *
 * This entity stores:
 * - Ownership information
 * - Item details
 * - Pricing information
 * - Availability window
 * - Current listing status
 * - Geographic location
 *
 * The location field uses PostGIS geography support to enable
 * efficient geospatial searches such as:
 * - Find items near a user
 * - Radius-based item discovery
 * - Distance-based ranking
 */
@Getter
@Setter
@Entity
@Table(name = "items", schema = "item_schema")
public class ItemEntity {

    /**
     * Unique identifier for the item.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Owner of the item.
     *
     * References the user who created the listing.
     */
    private Long ownerId;

    /**
     * Category to which this item belongs.
     *
     * Used for grouping and filtering items.
     */
    private Long categoryId;

    /**
     * Short user-facing title of the item.
     *
     * Example:
     * "Canon DSLR Camera"
     */
    private String title;

    /**
     * Detailed description of the item.
     *
     * Used by renters to understand the item's
     * condition, features, and usage guidelines.
     */
    private String description;

    /**
     * Rental cost charged per day.
     */
    private double pricePerDay;

    /**
     * Security deposit required before rental.
     *
     * Can be used to cover damages, loss,
     * or policy violations.
     */
    private double depositAmount;

    /**
     * Current status of the item listing.
     *
     * Example values:
     * - ACTIVE
     * - INACTIVE
     */
    @Enumerated(EnumType.STRING)
    private ItemStatus status;

    /**
     * Timestamp when the item was created.
     */
    private LocalDateTime createdAt;

    /**
     * Timestamp of the most recent modification.
     */
    private LocalDateTime updatedAt;

    /**
     * Earliest date the item can be rented.
     */
    private LocalDate availableFrom;

    /**
     * Latest date the item can be rented.
     */
    private LocalDate availableTo;

    /**
     * Geographic location of the item.
     *
     * Stored as a PostGIS geography point using
     * the WGS84 coordinate system (SRID 4326).
     *
     * Enables:
     * - Radius-based searches
     * - Distance calculations
     * - Location-aware ranking
     *
     * Coordinates:
     * - X = Longitude
     * - Y = Latitude
     */
    @Column(columnDefinition = "geography(Point,4326)")
    private Point location;
}