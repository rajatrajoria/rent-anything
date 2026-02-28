package com.rajat.rent_anything.user.exceptions;

import com.rajat.rent_anything.common.enums.ErrorCode;

public class UserOperationException extends UserException {
    public UserOperationException(ErrorCode errorCode) {
        super(errorCode);
    }
    public UserOperationException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
