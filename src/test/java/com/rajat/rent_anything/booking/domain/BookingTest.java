package com.rajat.rent_anything.booking.domain;

import com.rajat.rent_anything.booking.exceptions.InvalidBookingDatesException;
import com.rajat.rent_anything.booking.exceptions.SelfBookingExceptionNotAllowed;
import com.rajat.rent_anything.item.exceptions.InactiveItemException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BookingTest {

    private static final Long ITEM_ID = 1L;
    private static final Long RENTER_ID = 2L;
    private static final Long OWNER_ID = 3L;
    private static final LocalDate START = LocalDate.of(2026, 1, 10);
    private static final LocalDate END = LocalDate.of(2026, 1, 15);
    private static final double PRICE_PER_DAY = 100.0;

    @Test
    void create_succeedsAndStartsInPendingState() {
        Booking booking = Booking.create(ITEM_ID, RENTER_ID, OWNER_ID, true, START, END, PRICE_PER_DAY);

        assertThat(booking.getItemId()).isEqualTo(ITEM_ID);
        assertThat(booking.getRenterId()).isEqualTo(RENTER_ID);
        assertThat(booking.getStartDate()).isEqualTo(START);
        assertThat(booking.getEndDate()).isEqualTo(END);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING);
        assertThat(booking.getCreatedAt()).isNotNull();
        assertThat(booking.getUpdatedAt()).isNotNull();
    }

    @Test
    void create_calculatesInclusiveDayCountForAmount() {
        // 10th to 15th inclusive = 6 days
        Booking booking = Booking.create(ITEM_ID, RENTER_ID, OWNER_ID, true, START, END, PRICE_PER_DAY);

        assertThat(booking.getAmount()).isEqualTo(6 * PRICE_PER_DAY);
    }

    @Test
    void create_singleDayBooking_calculatesOneDayAmount() {
        Booking booking = Booking.create(ITEM_ID, RENTER_ID, OWNER_ID, true, START, START, PRICE_PER_DAY);

        assertThat(booking.getAmount()).isEqualTo(PRICE_PER_DAY);
    }

    @Test
    void create_startDateAfterEndDate_throwsInvalidBookingDatesException() {
        assertThrows(InvalidBookingDatesException.class, () ->
                Booking.create(ITEM_ID, RENTER_ID, OWNER_ID, true, END, START, PRICE_PER_DAY));
    }

    @Test
    void create_inactiveItem_throwsInactiveItemException() {
        assertThrows(InactiveItemException.class, () ->
                Booking.create(ITEM_ID, RENTER_ID, OWNER_ID, false, START, END, PRICE_PER_DAY));
    }

    @Test
    void create_renterIsOwner_throwsSelfBookingExceptionNotAllowed() {
        assertThrows(SelfBookingExceptionNotAllowed.class, () ->
                Booking.create(ITEM_ID, RENTER_ID, RENTER_ID, true, START, END, PRICE_PER_DAY));
    }

    @Test
    void rehydrate_restoresCorrectStateForEachStatus() {
        for (BookingStatus status : BookingStatus.values()) {
            Booking booking = Booking.rehydrate(
                    10L, ITEM_ID, RENTER_ID, START, END, 500.0, status,
                    LocalDateTime.now(), LocalDateTime.now()
            );
            assertThat(booking.getStatus()).isEqualTo(status);
        }
    }

    @Test
    void confirm_fromPending_transitionsToConfirmed() {
        Booking booking = Booking.create(ITEM_ID, RENTER_ID, OWNER_ID, true, START, END, PRICE_PER_DAY);

        booking.confirm();

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void cancel_fromPending_transitionsToCancelled() {
        Booking booking = Booking.create(ITEM_ID, RENTER_ID, OWNER_ID, true, START, END, PRICE_PER_DAY);

        booking.cancel();

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }
}
