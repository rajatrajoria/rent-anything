package com.rajat.rent_anything.common.exceptions;

import com.rajat.rent_anything.common.enums.ErrorCode;

public class InvalidSecurityOperation extends SecurityException {
    public InvalidSecurityOperation() {
        super(ErrorCode.INVALID_SECURITY_OPERATION);
    }
    public InvalidSecurityOperation(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
