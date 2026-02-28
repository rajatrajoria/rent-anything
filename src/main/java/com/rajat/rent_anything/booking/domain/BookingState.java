package com.rajat.rent_anything.booking.domain;

public interface BookingState {
    void confirm(Booking booking);
    void cancel(Booking booking);
    void expire(Booking booking);
    void complete(Booking booking);
}
