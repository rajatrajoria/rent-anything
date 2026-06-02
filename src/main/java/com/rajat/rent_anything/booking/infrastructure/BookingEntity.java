package com.rajat.rent_anything.booking.infrastructure;

import com.rajat.rent_anything.booking.domain.BookingStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Persistence entity representing a booking record.
 *
 * A booking represents a rental request for an item
 * during a specific date range.
 *
 * This entity stores the persisted state of a booking
 * and is mapped to the booking_schema.bookings table.
 *
 * Key Information Stored:
 * - Renter information
 * - Item being rented
 * - Rental period
 * - Booking amount
 * - Current booking status
 * - Audit timestamps
 *
 * The corresponding domain object (Booking) contains
 * the business rules and state-transition logic,
 * while this entity is responsible only for persistence.
 */
@Getter
@Setter
@Entity
@Table(name = "bookings", schema = "booking_schema")
public class BookingEntity {

    /**
     * Unique booking identifier.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Item associated with the booking.
     */
    private Long itemId;

    /**
     * User who created the booking request.
     */
    private Long renterId;

    /**
     * Rental start date.
     */
    private LocalDate startDate;

    /**
     * Rental end date.
     */
    private LocalDate endDate;

    /**
     * Total booking amount.
     *
     * Calculated when the booking is created and
     * persisted to maintain historical pricing accuracy.
     */
    private Double amount;

    /**
     * Current booking lifecycle status.
     *
     * Example values:
     * - PENDING
     * - CONFIRMED
     * - CANCELLED
     * - COMPLETED
     * - EXPIRED
     */
    @Enumerated(EnumType.STRING)
    private BookingStatus status;

    /**
     * Timestamp when the booking was created.
     */
    private LocalDateTime createdAt;

    /**
     * Timestamp of the most recent modification.
     */
    private LocalDateTime updatedAt;
}