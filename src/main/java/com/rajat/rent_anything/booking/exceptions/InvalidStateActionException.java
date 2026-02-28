package com.rajat.rent_anything.booking.exceptions;

import com.rajat.rent_anything.common.enums.ErrorCode;

public class InvalidStateActionException extends BookingException {
    public InvalidStateActionException() {
        super(ErrorCode.BOOKING_STATE_TRANSITION_INVALID);
    }

    public InvalidStateActionException(String message) {
        super(ErrorCode.BOOKING_STATE_TRANSITION_INVALID, message);
    }
}
