package com.rajat.rent_anything.booking.domain.state;

import com.rajat.rent_anything.booking.domain.Booking;
import com.rajat.rent_anything.booking.domain.BookingState;
import com.rajat.rent_anything.booking.exceptions.InvalidStateActionException;

public class ExpiredState implements BookingState {

    @Override
    public void confirm(Booking booking) {
        throw new InvalidStateActionException("Expired booking cannot be confirmed");
    }

    @Override
    public void cancel(Booking booking) {
        throw new InvalidStateActionException("Expired booking cannot be cancelled");
    }

    @Override
    public void expire(Booking booking) {
        throw new InvalidStateActionException("Booking is already expired");
    }

    @Override
    public void complete(Booking booking) {
        throw new InvalidStateActionException("Expired booking cannot be completed");
    }
}