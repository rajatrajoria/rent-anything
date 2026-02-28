package com.rajat.rent_anything.booking.exceptions;

import com.rajat.rent_anything.common.enums.ErrorCode;

public class BookingConflictException extends BookingException {

    public BookingConflictException(String message) {
        super(ErrorCode.BOOKING_CONFLICT, message);
    }

    public BookingConflictException() {
        super(ErrorCode.BOOKING_CONFLICT);
    }
}
