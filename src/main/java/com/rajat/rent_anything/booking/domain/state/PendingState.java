package com.rajat.rent_anything.booking.domain.state;

import com.rajat.rent_anything.booking.domain.Booking;
import com.rajat.rent_anything.booking.domain.BookingState;
import com.rajat.rent_anything.booking.domain.BookingStatus;
import com.rajat.rent_anything.booking.exceptions.InvalidStateActionException;

public class PendingState implements BookingState {

    @Override
    public void confirm(Booking booking) {
        booking.setState(new ConfirmedState(), BookingStatus.CONFIRMED);
    }

    @Override
    public void cancel(Booking booking) {
        booking.setState(new CancelledState(), BookingStatus.CANCELLED);
    }

    @Override
    public void expire(Booking booking) {
        booking.setState(new ExpiredState(), BookingStatus.EXPIRED);
    }

    @Override
    public void complete(Booking booking) {
        throw new InvalidStateActionException("Cannot complete a pending booking");
    }
}