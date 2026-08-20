package com.rajat.rent_anything.kyc.exceptions;

import com.rajat.rent_anything.common.enums.ErrorCode;

public class KycSubmissionStateException extends KycException {
    public KycSubmissionStateException(ErrorCode errorCode) {
        super(errorCode);
    }

    public KycSubmissionStateException(ErrorCode errorCode, String customMessage) {
        super(errorCode, customMessage);
    }
}
