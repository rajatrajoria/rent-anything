package com.rajat.rent_anything.booking.infrastructure;

import com.rajat.rent_anything.booking.domain.Booking;
/**
 * Mapper responsible for converting between:
 *
 * - Booking domain objects
 * - Booking persistence entities
 *
 * The domain model contains business rules, validations,
 * and state-transition behavior, while the entity model
 * represents how booking data is stored in the database.
 *
 * This separation helps keep business logic independent
 * from persistence concerns.
 */
public class BookingMapper {

    /**
     * Converts a Booking domain object into a persistence entity.
     *
     * Used when persisting new bookings or saving
     * booking state changes.
     *
     * @param booking domain booking
     * @return persistence entity
     */
    public static BookingEntity toEntity(Booking booking) {

        BookingEntity entity = new BookingEntity();

        entity.setId(booking.getId());
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

    /**
     * Converts a persistence entity into a Booking domain object.
     *
     * Rehydration restores the booking's business state,
     * including the appropriate state-machine implementation
     * associated with the persisted BookingStatus.
     *
     * @param entity persisted booking entity
     * @return domain booking
     */
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