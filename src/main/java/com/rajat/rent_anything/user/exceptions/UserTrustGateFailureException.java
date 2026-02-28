package com.rajat.rent_anything.user.exceptions;

import com.rajat.rent_anything.common.enums.ErrorCode;

public class UserTrustGateFailureException extends UserException {
    public UserTrustGateFailureException(ErrorCode errorCode) {
        super(errorCode);
    }
    public UserTrustGateFailureException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
