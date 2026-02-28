package com.rajat.rent_anything.common.exceptions;

import com.rajat.rent_anything.common.enums.ErrorCode;

public class InvalidTokenException extends SecurityException {
    public InvalidTokenException() {
        super(ErrorCode.INVALID_TOKEN);
    }
    public InvalidTokenException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
