package com.rajat.rent_anything.common.enums;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
    // ===== Booking =====
    BOOKING_NOT_FOUND("BKG_001", "Booking not found", HttpStatus.NOT_FOUND),
    BOOKING_CONFLICT("BKG_002", "Booking dates conflict", HttpStatus.CONFLICT),
    BOOKING_DATES_INVALID("BKG_003", "Invalid booking date range", HttpStatus.BAD_REQUEST),
    BOOKING_STATE_TRANSITION_INVALID("BKG_004", "Invalid booking state transition", HttpStatus.BAD_REQUEST),
    SELF_BOOKING_NOT_ALLOWED("BKG_005", "Cannot book your own item", HttpStatus.BAD_REQUEST),
    BOOKING_ACTION_UNAUTHORIZED("BKG_006", "Unauthorized booking action", HttpStatus.FORBIDDEN),

    // ===== Item =====
    ITEM_NOT_FOUND("ITM_001", "Item not found", HttpStatus.NOT_FOUND),
    ITEM_INACTIVE("ITM_002", "Item is not active", HttpStatus.BAD_REQUEST),
    INVALID_ITEM_EXCEPTION("ITM_003", "Invalid item data", HttpStatus.BAD_REQUEST),
    INVALID_ITEM_INPUT("ITM_004", "Invalid item input", HttpStatus.BAD_REQUEST),
    INVALID_ITEM_LOCATION("ITM_005", "Invalid item location", HttpStatus.BAD_REQUEST),
    INVALID_ITEM_PRICING("ITM_006", "Invalid item pricing", HttpStatus.BAD_REQUEST),
    INVALID_AVAILABILITY_WINDOW("ITM_007", "Invalid availability window", HttpStatus.BAD_REQUEST),
    ILLEGAL_ITEM_MODIFICATION("ITM_008", "Illegal item modification attempt", HttpStatus.FORBIDDEN),

    //===== User =====
    USER_NOT_FOUND("USR_001", "User not found", HttpStatus.NOT_FOUND),
    EMAIL_ALREADY_IN_USE("USR_002", "Email is already in use", HttpStatus.BAD_REQUEST),
    INVALID_PASSWORD("USR_003", "Invalid password", HttpStatus.UNAUTHORIZED),
    INVALID_USER_INPUT("USR_004", "Invalid user input", HttpStatus.BAD_REQUEST),
    TRUST_GATE_FAILURE("USR_005", "User does not meet trust requirements", HttpStatus.FORBIDDEN),
    USER_OPERATION_UNAUTHORIZED("USR_006", "Unauthorized user operation", HttpStatus.FORBIDDEN),

    //Security related
    INVALID_TOKEN("SEC_001", "Invalid authentication token", HttpStatus.UNAUTHORIZED),
    INVALID_SECURITY_OPERATION("SEC_002", "Invalid security operation", HttpStatus.FORBIDDEN);


    private final String code;
    private final String description;
    private final HttpStatus httpStatus;

    ErrorCode(String code, String description, HttpStatus httpStatus) {
        this.code = code;
        this.description = description;
        this.httpStatus = httpStatus;
    }
}
