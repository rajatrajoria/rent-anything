package com.rajat.rent_anything.user.exceptions;

import com.rajat.rent_anything.common.enums.ErrorCode;
import lombok.Getter;

@Getter
public class UserInputException extends UserException {
    public UserInputException(ErrorCode errorCode) {
        super(errorCode);
    }
    public UserInputException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
