package com.rajat.rent_anything.item.exceptions;

import com.rajat.rent_anything.common.enums.ErrorCode;
import lombok.Getter;

@Getter
public abstract class ItemException extends RuntimeException {
    private final ErrorCode errorCode;
    protected ItemException(ErrorCode errorCode) {
        super(errorCode.getDescription());
        this.errorCode = errorCode;
    }

    public ItemException(ErrorCode errorCode, String customMessage) {
        super(customMessage);
        this.errorCode = errorCode;
    }
}
