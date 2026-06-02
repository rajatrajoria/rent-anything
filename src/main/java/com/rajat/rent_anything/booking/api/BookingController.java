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
/**
 * REST controller responsible for booking lifecycle operations.
 *
 * Responsibilities:
 * - Create booking requests
 * - Confirm bookings
 * - Cancel bookings
 *
 * A booking represents a rental request made by a renter
 * for a specific item during a specified date range.
 *
 * Typical Booking Lifecycle:
 *
 * Create Booking
 *      |
 *      v
 *    PENDING
 *      |
 *      +----------------+
 *      |                |
 *      v                v
 * CONFIRMED        CANCELLED
 *
 * All booking operations are delegated to the BookingService,
 * which contains the core booking business rules.
 */

@RequestMapping("/api/bookings")
@RestController
public class BookingController {

    /**
     * Service responsible for booking-related business operations.
     */
    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    /**
     * Creates a new booking request.
     *
     * Workflow:
     * 1. Extract authenticated renter.
     * 2. Create booking command.
     * 3. Submit booking request.
     * 4. Return generated booking id.
     *
     * The authenticated user automatically becomes
     * the renter associated with the booking.
     *
     * @param request booking request details
     * @param userDetails authenticated user
     * @return newly created booking id
     */
    @PostMapping("/create")
    public ResponseEntity<ApiResponse<Long>> createBooking(
            @Valid @RequestBody BookingRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {

        Long renterId = userDetails.getDomainUser().getId();

        CreateBookingCommand command = new CreateBookingCommand(
                request.itemId(),
                renterId,
                request.startDate(),
                request.endDate()
        );

        Long bookingId = bookingService.createBooking(command);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(bookingId));
    }

    /**
     * Confirms an existing booking.
     *
     * A confirmed booking represents an accepted rental
     * agreement between the renter and item owner.
     *
     * Business validation is handled by BookingService.
     *
     * @param id booking identifier
     * @return success response
     */
    @PostMapping("/{id}/confirm")
    public ResponseEntity<ApiResponse<Void>> confirmBooking(
            @PathVariable Long id
    ) {

        bookingService.confirmBooking(id);

        return ResponseEntity.ok(ApiResponse.success());
    }

    /**
     * Cancels an existing booking.
     *
     * Once cancelled, the booking is no longer considered
     * active and should not block item availability.
     *
     * Business validation is handled by BookingService.
     *
     * @param id booking identifier
     * @return success response
     */
    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancelBooking(
            @PathVariable Long id
    ) {

        bookingService.cancelBooking(id);

        return ResponseEntity.ok(ApiResponse.success());
    }
}