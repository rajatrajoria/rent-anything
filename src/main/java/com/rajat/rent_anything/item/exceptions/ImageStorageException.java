package com.rajat.rent_anything.item.exceptions;

import com.rajat.rent_anything.common.enums.ErrorCode;

public class ImageStorageException extends ItemException {

    public ImageStorageException() {
        super(ErrorCode.IMAGE_STORAGE_FAILURE);
    }

    public ImageStorageException(String customMessage) {
        super(ErrorCode.IMAGE_STORAGE_FAILURE, customMessage);
    }
}