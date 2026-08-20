package com.rajat.rent_anything.kyc.exceptions;

import com.rajat.rent_anything.common.enums.ErrorCode;
import lombok.Getter;

@Getter
public abstract class KycException extends RuntimeException {
    private final ErrorCode errorCode;

    protected KycException(ErrorCode errorCode) {
        super(errorCode.getDescription());
        this.errorCode = errorCode;
    }

    protected KycException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
