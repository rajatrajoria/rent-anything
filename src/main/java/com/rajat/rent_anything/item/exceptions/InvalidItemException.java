package com.rajat.rent_anything.item.exceptions;

import com.rajat.rent_anything.common.enums.ErrorCode;
import lombok.Getter;

@Getter
public class InvalidItemException extends ItemException{
    public InvalidItemException(ErrorCode errorCode) {
        super(errorCode);
    }

    public InvalidItemException(ErrorCode errorCode, String customMessage) {
        super(errorCode, customMessage);
    }
}
