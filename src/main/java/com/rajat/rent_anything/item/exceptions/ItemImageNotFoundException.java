package com.rajat.rent_anything.item.exceptions;

import com.rajat.rent_anything.common.enums.ErrorCode;

public class ItemImageNotFoundException extends ItemException {
    public ItemImageNotFoundException() {
        super(ErrorCode.ITEM_IMAGE_NOT_FOUND);
    }

    public ItemImageNotFoundException(String customMessage) {
        super(ErrorCode.ITEM_IMAGE_NOT_FOUND, customMessage);
    }
}
