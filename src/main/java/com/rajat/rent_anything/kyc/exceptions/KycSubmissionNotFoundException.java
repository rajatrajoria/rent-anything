package com.rajat.rent_anything.kyc.exceptions;

import com.rajat.rent_anything.common.enums.ErrorCode;

public class KycSubmissionNotFoundException extends KycException {
    public KycSubmissionNotFoundException() {
        super(ErrorCode.KYC_SUBMISSION_NOT_FOUND);
    }

    public KycSubmissionNotFoundException(String customMessage) {
        super(ErrorCode.KYC_SUBMISSION_NOT_FOUND, customMessage);
    }
}
