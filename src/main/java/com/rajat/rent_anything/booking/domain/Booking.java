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
import java.util.List;

@Slf4j
@Getter
public class Booking {
    private Long id;
    private Long itemId;
    private Long renterId;
    private LocalDate startDate;
    private LocalDate endDate;
    private BookingStatus status;
    private transient BookingState state;
    private LocalDateTime createdAt;
    private Double amount;
    private LocalDateTime updatedAt;


    private Booking(){}

    public static Booking create(
            Long itemId,
            Long renterId,
            Long itemOwnerId,
            boolean isItemActive,
            LocalDate startDate,
            LocalDate endDate,
            Double itemPricePerDay
    ){
        if (startDate.isAfter(endDate)) {
            throw new InvalidBookingDatesException("Start date must be before end date");
        }

        if (!isItemActive) {
            throw new InactiveItemException("Item is not active");
        }

        if (renterId.equals(itemOwnerId)) {
            throw new SelfBookingExceptionNotAllowed("Cannot book your own item");
        }

        long days = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        double totalPrice = days * itemPricePerDay;

        Booking booking = new Booking();
        booking.itemId = itemId;
        booking.renterId = renterId;
        booking.startDate = startDate;
        booking.endDate = endDate;
        booking.setState(new PendingState(), BookingStatus.PENDING);
        booking.createdAt = LocalDateTime.now();
        booking.updatedAt = LocalDateTime.now();
        booking.amount = totalPrice;

        return booking;
    }

    public void confirm() {
        state.confirm(this);
    }

    public void cancel() {
        state.cancel(this);
    }

    public void expire() {
        state.expire(this);
    }

    public void complete() {
        state.complete(this);
    }

    public void setState(BookingState newState, BookingStatus newStatus) {
        this.state = newState;
        this.status = newStatus;
        this.updatedAt = LocalDateTime.now();
    }

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
