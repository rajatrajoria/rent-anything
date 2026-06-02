package com.rajat.rent_anything.booking.domain;

import com.rajat.rent_anything.booking.domain.state.*;
import com.rajat.rent_anything.booking.exceptions.InvalidBookingDatesException;
import com.rajat.rent_anything.booking.exceptions.SelfBookingExceptionNotAllowed;
import com.rajat.rent_anything.item.exceptions.InactiveItemException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Domain model representing a rental booking.
 *
 * A booking represents an agreement between:
 * - A renter
 * - An item owner
 * - A specific rental period
 *
 * Responsibilities:
 * - Enforce booking business rules.
 * - Manage booking lifecycle transitions.
 * - Calculate rental amount.
 * - Protect domain invariants.
 *
 * Booking Lifecycle:
 *
 * PENDING
 *    |
 *    +------------+
 *    |            |
 *    v            v
 * CONFIRMED   CANCELLED
 *    |
 *    +------------+
 *    |            |
 *    v            v
 * COMPLETED   EXPIRED
 *
 * This class uses the State Pattern to enforce valid
 * booking state transitions.
 */
@Slf4j
@Getter
public class Booking {

    /**
     * Unique booking identifier.
     */
    private Long id;

    /**
     * Item being rented.
     */
    private Long itemId;

    /**
     * User requesting the rental.
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
     * Current booking status.
     */
    private BookingStatus status;

    /**
     * Runtime state implementation used by the
     * State Pattern.
     *
     * Marked transient because state can be rebuilt
     * from the persisted BookingStatus.
     */
    private transient BookingState state;

    /**
     * Timestamp when the booking was created.
     */
    private LocalDateTime createdAt;

    /**
     * Total booking amount calculated from
     * rental duration and item pricing.
     */
    private Double amount;

    /**
     * Timestamp of the most recent booking update.
     */
    private LocalDateTime updatedAt;

    private Booking() {}

    /**
     * Creates a new booking.
     *
     * Domain Validations:
     * - Start date must not be after end date.
     * - Item must be active.
     * - Users cannot book their own items.
     *
     * Pricing:
     * Total amount is calculated using:
     *
     * Rental Days × Price Per Day
     *
     * Newly created bookings start in the PENDING state.
     *
     * @param itemId item being booked
     * @param renterId renter creating the booking
     * @param itemOwnerId owner of the item
     * @param isItemActive whether the item is active
     * @param startDate rental start date
     * @param endDate rental end date
     * @param itemPricePerDay daily rental rate
     * @return newly created booking
     */
    public static Booking create(
            Long itemId,
            Long renterId,
            Long itemOwnerId,
            boolean isItemActive,
            LocalDate startDate,
            LocalDate endDate,
            Double itemPricePerDay
    ) {

        if (startDate.isAfter(endDate)) {
            throw new InvalidBookingDatesException(
                    "Start date must be before end date"
            );
        }

        if (!isItemActive) {
            throw new InactiveItemException(
                    "Item is not active"
            );
        }

        // Prevent users from renting their own items.
        if (renterId.equals(itemOwnerId)) {
            throw new SelfBookingExceptionNotAllowed(
                    "Cannot book your own item"
            );
        }

        // Inclusive day calculation.
        long days =
                ChronoUnit.DAYS.between(startDate, endDate) + 1;

        double totalPrice = days * itemPricePerDay;

        Booking booking = new Booking();

        booking.itemId = itemId;
        booking.renterId = renterId;
        booking.startDate = startDate;
        booking.endDate = endDate;

        booking.setState(
                new PendingState(),
                BookingStatus.PENDING
        );

        booking.createdAt = LocalDateTime.now();
        booking.updatedAt = LocalDateTime.now();
        booking.amount = totalPrice;

        return booking;
    }

    /**
     * Transitions booking to CONFIRMED state.
     *
     * Validation is delegated to the current state implementation.
     */
    public void confirm() {
        state.confirm(this);
    }

    /**
     * Transitions booking to CANCELLED state.
     *
     * Validation is delegated to the current state implementation.
     */
    public void cancel() {
        state.cancel(this);
    }

    /**
     * Transitions booking to EXPIRED state.
     *
     * Validation is delegated to the current state implementation.
     */
    public void expire() {
        state.expire(this);
    }

    /**
     * Transitions booking to COMPLETED state.
     *
     * Validation is delegated to the current state implementation.
     */
    public void complete() {
        state.complete(this);
    }

    /**
     * Updates both the runtime state implementation
     * and persisted booking status.
     *
     * This method should only be called by state classes.
     *
     * @param newState new runtime state
     * @param newStatus new persisted status
     */
    public void setState(
            BookingState newState,
            BookingStatus newStatus
    ) {

        this.state = newState;
        this.status = newStatus;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Reconstructs a booking from persisted data.
     *
     * Unlike create(), this method skips business validations
     * because the booking already exists in the system.
     *
     * The appropriate runtime state implementation is rebuilt
     * from the stored BookingStatus.
     *
     * @return rehydrated booking
     */
    public static Booking rehydrate(
            Long id,
            Long itemId,
            Long renterId,
            LocalDate startDate,
            LocalDate endDate,
            Double totalPrice,
            BookingStatus status,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {

        Booking booking = new Booking();

        booking.id = id;
        booking.itemId = itemId;
        booking.renterId = renterId;
        booking.startDate = startDate;
        booking.endDate = endDate;
        booking.amount = totalPrice;
        booking.status = status;
        booking.createdAt = createdAt;
        booking.updatedAt = updatedAt;

        booking.initializeStateFromStatus();

        return booking;
    }

    /**
     * Restores the correct runtime state implementation
     * from the persisted booking status.
     *
     * This is required because state objects themselves
     * are not stored in the database.
     */
    private void initializeStateFromStatus() {

        switch (this.status) {
            case PENDING -> this.state = new PendingState();
            case CONFIRMED -> this.state = new ConfirmedState();
            case CANCELLED -> this.state = new CancelledState();
            case COMPLETED -> this.state = new CompletedState();
            case EXPIRED -> this.state = new ExpiredState();
        }
    }
}
