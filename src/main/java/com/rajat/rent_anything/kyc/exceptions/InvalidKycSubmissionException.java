package com.rajat.rent_anything.kyc.exceptions;

import com.rajat.rent_anything.common.enums.ErrorCode;

public class InvalidKycSubmissionException extends KycException {
    public InvalidKycSubmissionException(ErrorCode errorCode) {
        super(errorCode);
    }

    public InvalidKycSubmissionException(ErrorCode errorCode, String customMessage) {
        super(errorCode, customMessage);
    }
}
