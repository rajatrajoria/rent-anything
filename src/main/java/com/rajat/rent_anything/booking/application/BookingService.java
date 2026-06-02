package com.rajat.rent_anything.booking.application;

import com.rajat.rent_anything.booking.application.commands.CreateBookingCommand;
import com.rajat.rent_anything.booking.domain.Booking;
import com.rajat.rent_anything.booking.domain.BookingStatus;
import com.rajat.rent_anything.booking.exceptions.InvalidBookingDatesException;
import com.rajat.rent_anything.booking.exceptions.NoSuchBookingException;
import com.rajat.rent_anything.booking.infrastructure.BookingEntity;
import com.rajat.rent_anything.booking.infrastructure.BookingMapper;
import com.rajat.rent_anything.booking.infrastructure.BookingRepository;
import com.rajat.rent_anything.item.domain.ItemStatus;
import com.rajat.rent_anything.item.exceptions.ItemNotFoundException;
import com.rajat.rent_anything.item.infrastructure.ItemEntity;
import com.rajat.rent_anything.item.infrastructure.ItemRepository;
import com.rajat.rent_anything.booking.exceptions.BookingConflictException;
import com.rajat.rent_anything.user.application.TrustGateService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
/**
 * Service responsible for managing the booking lifecycle.
 *
 * Responsibilities:
 * - Create bookings
 * - Validate booking requests
 * - Detect booking conflicts
 * - Confirm bookings
 * - Cancel bookings
 *
 * Business Rules:
 * - Only trusted users can create bookings.
 * - Bookings must fall within item availability.
 * - Bookings cannot overlap existing active bookings.
 * - Only ACTIVE items can be booked.
 *
 * This service acts as the central coordinator between
 * items, bookings, and trust validation.
 */
@Transactional
@Service
public class BookingService {

    /**
     * Repository responsible for booking persistence operations.
     */
    private final BookingRepository bookingRepository;

    /**
     * Repository used to retrieve item information
     * during booking validation.
     */
    private final ItemRepository itemRepository;

    /**
     * Service responsible for enforcing trust-gated operations.
     *
     * Only trusted users may participate in marketplace
     * transactions such as bookings.
     */
    private final TrustGateService trustGateService;

    public BookingService(
            BookingRepository bookingRepository,
            ItemRepository itemRepository,
            TrustGateService trustGateService
    ) {
        this.bookingRepository = bookingRepository;
        this.itemRepository = itemRepository;
        this.trustGateService = trustGateService;
    }

    /**
     * Creates a new booking request.
     *
     * Validation Steps:
     * 1. Verify renter is trusted.
     * 2. Verify item exists.
     * 3. Verify booking dates fall within item availability.
     * 4. Check for overlapping active bookings.
     * 5. Create booking in PENDING state.
     *
     * Concurrency Protection:
     * Booking conflicts are checked both:
     * - At the application layer.
     * - At the database layer.
     *
     * The database constraint acts as the final safeguard
     * against race conditions when multiple users attempt
     * to book the same item simultaneously.
     *
     * @param command booking creation request
     * @return newly created booking id
     * @throws InvalidBookingDatesException when booking dates
     *                                      fall outside item availability
     * @throws BookingConflictException when the requested
     *                                  date range is already booked
     */
    @Transactional
    public Long createBooking(CreateBookingCommand command) {

        // Only trusted users are allowed to make bookings.
        trustGateService.ensureUserIsTrusted(command.renterId());

        ItemEntity itemEntity = itemRepository.findById(command.itemId())
                .orElseThrow(() ->
                        new ItemNotFoundException("Item not found")
                );

        // Ensure requested dates are within the owner's
        // declared availability window.
        if (command.startDate().isBefore(itemEntity.getAvailableFrom())
                || command.endDate().isAfter(itemEntity.getAvailableTo())) {

            throw new InvalidBookingDatesException(
                    "Booking dates outside item availability"
            );
        }

        // Check for overlapping active bookings.
        List<BookingEntity> conflictEntities =
                bookingRepository.findConflictingBookings(
                        command.itemId(),
                        List.of(
                                BookingStatus.PENDING,
                                BookingStatus.CONFIRMED
                        ),
                        command.endDate(),
                        command.startDate()
                );

        if (!conflictEntities.isEmpty()) {
            throw new BookingConflictException(
                    "Dates are already booked for the item"
            );
        }

        Booking booking = Booking.create(
                command.itemId(),
                command.renterId(),
                itemEntity.getOwnerId(),
                itemEntity.getStatus() == ItemStatus.ACTIVE,
                command.startDate(),
                command.endDate(),
                itemEntity.getPricePerDay()
        );


        try {

            BookingEntity bookingEntityToSaveInDb =
                    BookingMapper.toEntity(booking);

            BookingEntity savedBooking = bookingRepository.save(bookingEntityToSaveInDb);

            return savedBooking.getId();

        } catch (DataIntegrityViolationException ex) {

            // Handles race conditions where another booking
            // is inserted after the conflict check but before
            // the current transaction commits.
            throw new BookingConflictException(
                    "Dates are already booked for the item"
            );
        }
    }

    /**
     * Confirms a booking.
     *
     * A confirmed booking represents an accepted rental
     * agreement between the renter and item owner.
     *
     * State Transition:
     *
     * PENDING -> CONFIRMED
     *
     * @param bookingId booking identifier
     * @throws NoSuchBookingException when the booking does not exist
     */
    @Transactional
    public void confirmBooking(Long bookingId) {

        BookingEntity entity = bookingRepository.findById(bookingId)
                .orElseThrow(() ->
                        new NoSuchBookingException("Booking not found")
                );

        Booking booking = BookingMapper.toDomain(entity);

        booking.confirm();

        bookingRepository.save(
                BookingMapper.toEntity(booking)
        );
    }

    /**
     * Cancels a booking.
     *
     * State Transition:
     *
     * PENDING   -> CANCELLED
     * CONFIRMED -> CANCELLED
     *
     * Once cancelled, the booking should no longer
     * block item availability.
     *
     * @param bookingId booking identifier
     * @throws NoSuchBookingException when the booking does not exist
     */
    @Transactional
    public void cancelBooking(Long bookingId) {

        BookingEntity entity = bookingRepository.findById(bookingId)
                .orElseThrow(() ->
                        new NoSuchBookingException("Booking not found")
                );

        Booking booking = BookingMapper.toDomain(entity);

        booking.cancel();

        bookingRepository.save(
                BookingMapper.toEntity(booking)
        );
    }
}