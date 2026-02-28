package com.rajat.rent_anything.common;

import com.rajat.rent_anything.booking.exceptions.BookingConflictException;
import com.rajat.rent_anything.booking.exceptions.BookingException;
import com.rajat.rent_anything.common.enums.ErrorCode;
import com.rajat.rent_anything.common.model.ApiError;
import com.rajat.rent_anything.common.model.ApiResponse;
import com.rajat.rent_anything.item.exceptions.ItemException;
import com.rajat.rent_anything.user.exceptions.UserException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // =========================
    // USER
    // =========================
    @ExceptionHandler(UserException.class)
    public ResponseEntity<ApiResponse<Object>> handleUserException(
            UserException ex,
            HttpServletRequest request) {
        return buildErrorResponse(ex.getErrorCode(), ex.getMessage(), request);
    }

    // =========================
    // BOOKING
    // =========================
    @ExceptionHandler(BookingException.class)
    public ResponseEntity<ApiResponse<Object>> handleBookingException(
            BookingException ex,
            HttpServletRequest request) {
        return buildErrorResponse(ex.getErrorCode(), ex.getMessage(), request);
    }

    // =========================
    // ITEM
    // =========================
    @ExceptionHandler(ItemException.class)
    public ResponseEntity<ApiResponse<Object>> handleItemException(
            ItemException ex,
            HttpServletRequest request) {
        return buildErrorResponse(ex.getErrorCode(), ex.getMessage(), request);
    }

    // =========================
    // VALIDATION
    // =========================
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleValidationException(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {

        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("Validation error");

        return buildManualErrorResponse(
                400,
                "VAL_001",
                "VALIDATION_ERROR",
                message,
                request
        );
    }

    // =========================
    // GENERIC FALLBACK
    // =========================
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleGenericException(
            Exception ex,
            HttpServletRequest request) {

        return buildManualErrorResponse(
                500,
                "GEN_001",
                "INTERNAL_SERVER_ERROR",
                "Something went wrong",
                request
        );
    }

    // =====================================================
    //  COMMON ERROR RESPONSE BUILDER
    // =====================================================

    private ResponseEntity<ApiResponse<Object>> buildErrorResponse(
            ErrorCode errorCode,
            String message,
            HttpServletRequest request) {

        ApiError apiError = ApiError.builder()
                .timeStamp(LocalDateTime.now())
                .status(errorCode.getHttpStatus().value())
                .errorCode(errorCode.getCode())
                .errorName(errorCode.name())
                .message(message)
                .path(request.getRequestURI())
                .build();

        ApiResponse<Object> response = ApiResponse.builder()
                .success(false)
                .error(apiError)
                .build();

        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(response);
    }

    private ResponseEntity<ApiResponse<Object>> buildManualErrorResponse(
            int status,
            String errorCode,
            String errorName,
            String message,
            HttpServletRequest request) {

        ApiError apiError = ApiError.builder()
                .timeStamp(LocalDateTime.now())
                .status(status)
                .errorCode(errorCode)
                .errorName(errorName)
                .message(message)
                .path(request.getRequestURI())
                .build();

        ApiResponse<Object> response = ApiResponse.builder()
                .success(false)
                .error(apiError)
                .build();

        return ResponseEntity.status(status).body(response);
    }
}
