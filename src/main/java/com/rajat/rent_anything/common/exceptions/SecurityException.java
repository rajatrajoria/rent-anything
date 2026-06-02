package com.rajat.rent_anything.common.exceptions;

import com.rajat.rent_anything.common.enums.ErrorCode;

public abstract class SecurityException extends RuntimeException {
    @SuppressWarnings("unused")
    private final ErrorCode errorCode;
    protected SecurityException(ErrorCode errorCode) {
        super(errorCode.getDescription());
        this.errorCode = errorCode;
    }
    protected SecurityException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
