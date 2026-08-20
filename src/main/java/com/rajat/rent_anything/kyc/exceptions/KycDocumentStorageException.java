package com.rajat.rent_anything.kyc.exceptions;

import com.rajat.rent_anything.common.enums.ErrorCode;

public class KycDocumentStorageException extends KycException {
    public KycDocumentStorageException() {
        super(ErrorCode.KYC_DOCUMENT_STORAGE_FAILURE);
    }

    public KycDocumentStorageException(String customMessage) {
        super(ErrorCode.KYC_DOCUMENT_STORAGE_FAILURE, customMessage);
    }
}
