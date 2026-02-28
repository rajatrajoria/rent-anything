package com.rajat.rent_anything.booking.api;

import com.rajat.rent_anything.booking.application.BookingService;
import com.rajat.rent_anything.booking.application.commands.CreateBookingCommand;
import com.rajat.rent_anything.common.model.ApiResponse;
import com.rajat.rent_anything.security.CustomUserDetails;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RequestMapping("/api/bookings")
@RestController
public class BookingController {
    private final BookingService bookingService;

    public BookingController(BookingService bookingService){
        this.bookingService = bookingService;
    }

    // Create New Booking
    @PostMapping("/create")
    public ResponseEntity<ApiResponse<Long>> createBooking(@Valid @RequestBody BookingRequest request, @AuthenticationPrincipal CustomUserDetails userDetails){
        Long renterId = userDetails.getDomainUser().getId();
        CreateBookingCommand command = new CreateBookingCommand(
                request.itemId(),
                renterId,
                request.startDate(),
                request.endDate()
        );
        Long bookingId = bookingService.createBooking(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(bookingId));
    }

    // Confirm Booking
    @PostMapping("/{id}/confirm")
    public ResponseEntity<ApiResponse<Void>> confirmBooking(@PathVariable Long id) {
        bookingService.confirmBooking(id);
        return ResponseEntity.ok(ApiResponse.success());
    }

    // Cancel Booking
    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancelBooking(@PathVariable Long id) {
        bookingService.cancelBooking(id);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
