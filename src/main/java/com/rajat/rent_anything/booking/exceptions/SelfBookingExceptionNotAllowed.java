package com.rajat.rent_anything.booking.exceptions;

import com.rajat.rent_anything.common.enums.ErrorCode;

public class SelfBookingExceptionNotAllowed extends BookingException {
    public SelfBookingExceptionNotAllowed() {
        super(ErrorCode.SELF_BOOKING_NOT_ALLOWED);
    }
    public SelfBookingExceptionNotAllowed(String customMessage) {
        super(ErrorCode.SELF_BOOKING_NOT_ALLOWED, customMessage);
    }
}
