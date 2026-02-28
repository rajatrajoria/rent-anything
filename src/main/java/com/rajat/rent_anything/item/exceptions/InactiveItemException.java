package com.rajat.rent_anything.item.exceptions;

import com.rajat.rent_anything.common.enums.ErrorCode;

public class InactiveItemException extends ItemException {
    public InactiveItemException() {
        super(ErrorCode.ITEM_INACTIVE);
    }

    public InactiveItemException(String customMessage) {
        super(ErrorCode.ITEM_INACTIVE, customMessage);
    }
}
