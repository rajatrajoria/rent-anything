package com.rajat.rent_anything.booking.exceptions;

import com.rajat.rent_anything.common.enums.ErrorCode;

public class NoSuchBookingException extends BookingException {
    public NoSuchBookingException() {
        super(ErrorCode.BOOKING_NOT_FOUND);
    }

    public NoSuchBookingException(String message) {
        super(ErrorCode.BOOKING_NOT_FOUND, message);
    }
}
