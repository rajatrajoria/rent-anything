package com.rajat.rent_anything.booking.infrastructure;

import com.rajat.rent_anything.booking.domain.Booking;

public class BookingMapper {

    public static BookingEntity toEntity(Booking booking) {
        BookingEntity entity = new BookingEntity();
        entity.setItemId(booking.getItemId());
        entity.setRenterId(booking.getRenterId());
        entity.setStartDate(booking.getStartDate());
        entity.setEndDate(booking.getEndDate());
        entity.setAmount(booking.getAmount());
        entity.setStatus(booking.getStatus());
        entity.setCreatedAt(booking.getCreatedAt());
        entity.setUpdatedAt(booking.getUpdatedAt());
        return entity;
    }

    public static Booking toDomain(BookingEntity entity) {
        return Booking.rehydrate(
                entity.getId(),
                entity.getItemId(),
                entity.getRenterId(),
                entity.getStartDate(),
                entity.getEndDate(),
                entity.getAmount(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}

