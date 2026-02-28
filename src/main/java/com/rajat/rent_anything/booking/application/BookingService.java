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
import com.rajat.rent_anything.user.exceptions.UserTrustGateFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Transactional
@Service
public class BookingService {
    private final BookingRepository bookingRepository;
    private final ItemRepository itemRepository;
    private final TrustGateService trustGateService;

    public BookingService(BookingRepository bookingRepository, ItemRepository itemRepository, TrustGateService trustGateService) {
        this.bookingRepository = bookingRepository;
        this.itemRepository = itemRepository;
        this.trustGateService = trustGateService;
    }

    @Transactional
    public Long createBooking(CreateBookingCommand command){
        trustGateService.ensureUserIsTrusted(command.renterId());
        ItemEntity itemEntity = itemRepository.findById(command.itemId()).orElseThrow(() -> new ItemNotFoundException("Item not found"));

        if (command.startDate().isBefore(itemEntity.getAvailableFrom()) || command.endDate().isAfter(itemEntity.getAvailableTo())) {
            throw new InvalidBookingDatesException("Booking dates outside item availability");
        }

        List<BookingEntity> conflictEntities = bookingRepository.findConflictingBookings(
                command.itemId(),
                List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED),
                command.endDate(),
                command.startDate()
        );
        if(!conflictEntities.isEmpty()){
            throw new BookingConflictException("Dates are already booked for the item");
        }

        Booking booking = Booking.create(
                command.itemId(),
                command.renterId(),
                itemEntity.getOwnerId(),
                itemEntity.getStatus()==ItemStatus.ACTIVE,
                command.startDate(),
                command.endDate(),
                itemEntity.getPricePerDay()
        );
        try{
            BookingEntity bookingEntityToSaveInDb = BookingMapper.toEntity(booking);
            bookingRepository.save(bookingEntityToSaveInDb);
        } catch (DataIntegrityViolationException ex){
            throw new BookingConflictException("Dates are already booked for the item");
        }
        return booking.getId();
    }

    @Transactional
    public void confirmBooking(Long bookingId) {
        BookingEntity entity = bookingRepository.findById(bookingId).orElseThrow(() -> new NoSuchBookingException("Booking not found"));
        Booking booking = BookingMapper.toDomain(entity);
        booking.confirm();
        bookingRepository.save(BookingMapper.toEntity(booking));
    }

    @Transactional
    public void cancelBooking(Long bookingId) {
        BookingEntity entity = bookingRepository.findById(bookingId).orElseThrow(() -> new NoSuchBookingException("Booking not found"));
        Booking booking = BookingMapper.toDomain(entity);
        booking.cancel();
        bookingRepository.save(BookingMapper.toEntity(booking));
    }
}
