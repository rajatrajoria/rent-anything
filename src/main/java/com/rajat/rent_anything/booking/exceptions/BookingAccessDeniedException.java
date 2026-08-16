package com.rajat.rent_anything.booking.exceptions;

import com.rajat.rent_anything.common.enums.ErrorCode;

public class BookingAccessDeniedException extends BookingException {
    public BookingAccessDeniedException() {
        super(ErrorCode.BOOKING_ACTION_UNAUTHORIZED);
    }

    public BookingAccessDeniedException(String message) {
        super(ErrorCode.BOOKING_ACTION_UNAUTHORIZED, message);
    }
}
