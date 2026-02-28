package com.rajat.rent_anything.user.exceptions;

import com.rajat.rent_anything.common.enums.ErrorCode;
import lombok.Getter;

@Getter
public abstract class UserException extends RuntimeException {
    private final ErrorCode errorCode;
    protected UserException(ErrorCode errorCode) {
        super(errorCode.getDescription());
        this.errorCode = errorCode;
    }
    protected UserException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
