package com.rajat.rent_anything.booking.domain;

import com.rajat.rent_anything.booking.exceptions.InvalidStateActionException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Exhaustively exercises every (state, action) combination of the booking
 * state machine (PendingState, ConfirmedState, CancelledState, ExpiredState,
 * CompletedState) to make sure only the documented transitions succeed and
 * everything else raises InvalidStateActionException.
 */
class BookingStateMachineTest {

    private enum Action {CONFIRM, CANCEL, EXPIRE, COMPLETE}

    private static Booking bookingIn(BookingStatus status) {
        return Booking.rehydrate(
                1L, 10L, 20L,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 5),
                500.0,
                status,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
    }

    private static void apply(Booking booking, Action action) {
        switch (action) {
            case CONFIRM -> booking.confirm();
            case CANCEL -> booking.cancel();
            case EXPIRE -> booking.expire();
            case COMPLETE -> booking.complete();
        }
    }

    static Stream<Arguments> transitions() {
        return Stream.of(
                // PENDING
                Arguments.of(BookingStatus.PENDING, Action.CONFIRM, BookingStatus.CONFIRMED),
                Arguments.of(BookingStatus.PENDING, Action.CANCEL, BookingStatus.CANCELLED),
                Arguments.of(BookingStatus.PENDING, Action.EXPIRE, BookingStatus.EXPIRED),
                Arguments.of(BookingStatus.PENDING, Action.COMPLETE, null),

                // CONFIRMED
                Arguments.of(BookingStatus.CONFIRMED, Action.CONFIRM, null),
                Arguments.of(BookingStatus.CONFIRMED, Action.CANCEL, BookingStatus.CANCELLED),
                Arguments.of(BookingStatus.CONFIRMED, Action.EXPIRE, null),
                Arguments.of(BookingStatus.CONFIRMED, Action.COMPLETE, BookingStatus.COMPLETED),

                // CANCELLED - terminal, everything fails
                Arguments.of(BookingStatus.CANCELLED, Action.CONFIRM, null),
                Arguments.of(BookingStatus.CANCELLED, Action.CANCEL, null),
                Arguments.of(BookingStatus.CANCELLED, Action.EXPIRE, null),
                Arguments.of(BookingStatus.CANCELLED, Action.COMPLETE, null),

                // EXPIRED - terminal, everything fails
                Arguments.of(BookingStatus.EXPIRED, Action.CONFIRM, null),
                Arguments.of(BookingStatus.EXPIRED, Action.CANCEL, null),
                Arguments.of(BookingStatus.EXPIRED, Action.EXPIRE, null),
                Arguments.of(BookingStatus.EXPIRED, Action.COMPLETE, null),

                // COMPLETED - terminal, everything fails
                Arguments.of(BookingStatus.COMPLETED, Action.CONFIRM, null),
                Arguments.of(BookingStatus.COMPLETED, Action.CANCEL, null),
                Arguments.of(BookingStatus.COMPLETED, Action.EXPIRE, null),
                Arguments.of(BookingStatus.COMPLETED, Action.COMPLETE, null)
        );
    }

    @ParameterizedTest(name = "{0} -- {1} --> {2}")
    @MethodSource("transitions")
    void transition(BookingStatus initialStatus, Action action, BookingStatus expectedStatus) {
        Booking booking = bookingIn(initialStatus);

        if (expectedStatus == null) {
            assertThrows(InvalidStateActionException.class, () -> apply(booking, action));
            // status must remain unchanged after a rejected transition
            assertThat(booking.getStatus()).isEqualTo(initialStatus);
        } else {
            apply(booking, action);
            assertThat(booking.getStatus()).isEqualTo(expectedStatus);
        }
    }
}
