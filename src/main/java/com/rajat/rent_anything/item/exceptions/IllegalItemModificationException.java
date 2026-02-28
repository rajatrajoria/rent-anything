package com.rajat.rent_anything.item.exceptions;

import com.rajat.rent_anything.common.enums.ErrorCode;

public class IllegalItemModificationException extends ItemException {
    public IllegalItemModificationException() {
        super(ErrorCode.ILLEGAL_ITEM_MODIFICATION);
    }

    public IllegalItemModificationException(String customMessage) {
        super(ErrorCode.ILLEGAL_ITEM_MODIFICATION, customMessage);
    }
}
