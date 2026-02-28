package com.rajat.rent_anything.booking.domain.state;

import com.rajat.rent_anything.booking.domain.Booking;
import com.rajat.rent_anything.booking.domain.BookingState;
import com.rajat.rent_anything.booking.domain.BookingStatus;
import com.rajat.rent_anything.booking.exceptions.InvalidStateActionException;

public class ConfirmedState implements BookingState {

    @Override
    public void confirm(Booking booking) {
        throw new InvalidStateActionException("Already confirmed");
    }

    @Override
    public void cancel(Booking booking) {
        booking.setState(new CancelledState(), BookingStatus.CANCELLED);
    }

    @Override
    public void expire(Booking booking) {
        throw new InvalidStateActionException("Confirmed booking cannot expire");
    }

    @Override
    public void complete(Booking booking) {
        booking.setState(new CompletedState(), BookingStatus.COMPLETED);
    }
}
