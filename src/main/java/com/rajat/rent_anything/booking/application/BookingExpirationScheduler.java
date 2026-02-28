package com.rajat.rent_anything.booking.application;

import com.rajat.rent_anything.booking.domain.Booking;
import com.rajat.rent_anything.booking.domain.BookingStatus;
import com.rajat.rent_anything.booking.infrastructure.BookingEntity;
import com.rajat.rent_anything.booking.infrastructure.BookingMapper;
import com.rajat.rent_anything.booking.infrastructure.BookingRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
public class BookingExpirationScheduler {
    private final BookingRepository bookingRepository;
    @Value("${booking.expiration.timeout-minutes}")
    private int timeOutMinutes;

    public BookingExpirationScheduler(BookingRepository bookingRepository) {
        this.bookingRepository = bookingRepository;
    }

    @Scheduled(fixedRateString = "${booking.expiration.fixed-rate}")
    @Transactional
    public void expirePendingBookings() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(timeOutMinutes);

        List<BookingEntity> expiredCandidates =
                bookingRepository.findByStatusAndCreatedAtBefore(
                        BookingStatus.PENDING,
                        cutoff
                );

        for (BookingEntity entity : expiredCandidates) {
            Booking booking = BookingMapper.toDomain(entity);
            try {
                booking.expire();
                bookingRepository.save(BookingMapper.toEntity(booking));
            } catch (Exception ex) {
                log.info("Exception occurred in scheduler", ex);
            }
        }
        }
}
