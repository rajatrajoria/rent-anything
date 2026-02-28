package com.rajat.rent_anything.booking.application;

import com.rajat.rent_anything.booking.domain.Booking;
import com.rajat.rent_anything.booking.domain.BookingStatus;
import com.rajat.rent_anything.booking.infrastructure.BookingEntity;
import com.rajat.rent_anything.booking.infrastructure.BookingMapper;
import com.rajat.rent_anything.booking.infrastructure.BookingRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Component
public class BookingCompletionScheduler {
    private final BookingRepository bookingRepository;

    public BookingCompletionScheduler(BookingRepository bookingRepository) {
        this.bookingRepository = bookingRepository;
    }

    @Scheduled(fixedRateString = "${booking.completion.fixed-rate}")
    @Transactional
    public void completeFinishedBookings() {
        LocalDate today = LocalDate.now();
        List<BookingEntity> candidates =
                bookingRepository.findByStatusAndEndDateBefore(
                        BookingStatus.CONFIRMED,
                        today
                );
        for (BookingEntity entity : candidates) {
            Booking booking = BookingMapper.toDomain(entity);
            try {
                booking.complete();
                bookingRepository.save(BookingMapper.toEntity(booking));
            } catch (Exception ignored) {
            }
        }
    }
}
