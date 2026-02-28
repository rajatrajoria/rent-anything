package com.rajat.rent_anything.item.exceptions;

import com.rajat.rent_anything.common.enums.ErrorCode;

public class ItemNotFoundException extends ItemException{
    public ItemNotFoundException() {
        super(ErrorCode.ITEM_NOT_FOUND);
    }

    public ItemNotFoundException(String customMessage) {
        super(ErrorCode.ITEM_NOT_FOUND, customMessage);
    }
}
