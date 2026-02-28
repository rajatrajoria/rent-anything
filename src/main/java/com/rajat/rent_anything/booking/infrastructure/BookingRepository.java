package com.rajat.rent_anything.booking.infrastructure;

import com.rajat.rent_anything.booking.domain.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface BookingRepository extends JpaRepository<BookingEntity, Long> {
    @Query("""
        SELECT b FROM BookingEntity b
        WHERE b.itemId = :itemId
        AND b.status IN :statuses
        AND b.startDate <= :endDate
        AND b.endDate >= :startDate
    """)
    List<BookingEntity> findConflictingBookings(
            @Param("itemId") Long itemId,
            @Param("statuses") List<BookingStatus> statuses,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    List<BookingEntity> findByStatusAndCreatedAtBefore(
            BookingStatus status,
            LocalDateTime cutoffTime
    );

    List<BookingEntity> findByStatusAndEndDateBefore(
            BookingStatus status,
            LocalDate today
    );

    @Query("""
        SELECT b FROM BookingEntity b
        WHERE b.itemId = :itemId
        AND b.status IN :statuses
    """)
    List<BookingEntity> findByItemIdAndStatusIn(
            @Param("itemId") Long itemId,
            @Param("statuses") List<BookingStatus> statuses
    );
}
