package com.rajat.rent_anything.booking.exceptions;

import com.rajat.rent_anything.common.enums.ErrorCode;

public class InvalidBookingDatesException extends BookingException {
    public InvalidBookingDatesException() {
        super(ErrorCode.BOOKING_DATES_INVALID);
    }

    public InvalidBookingDatesException(String message) {
        super(ErrorCode.BOOKING_DATES_INVALID, message);
    }
}
