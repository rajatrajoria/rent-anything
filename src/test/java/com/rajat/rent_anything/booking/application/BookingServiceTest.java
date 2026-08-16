package com.rajat.rent_anything.booking.application;

import com.rajat.rent_anything.booking.application.commands.CreateBookingCommand;
import com.rajat.rent_anything.booking.domain.BookingStatus;
import com.rajat.rent_anything.booking.exceptions.BookingAccessDeniedException;
import com.rajat.rent_anything.booking.exceptions.BookingConflictException;
import com.rajat.rent_anything.booking.exceptions.InvalidBookingDatesException;
import com.rajat.rent_anything.booking.exceptions.NoSuchBookingException;
import com.rajat.rent_anything.booking.exceptions.SelfBookingExceptionNotAllowed;
import com.rajat.rent_anything.booking.infrastructure.BookingEntity;
import com.rajat.rent_anything.booking.infrastructure.BookingRepository;
import com.rajat.rent_anything.item.domain.ItemStatus;
import com.rajat.rent_anything.item.exceptions.InactiveItemException;
import com.rajat.rent_anything.item.exceptions.ItemNotFoundException;
import com.rajat.rent_anything.item.infrastructure.ItemEntity;
import com.rajat.rent_anything.item.infrastructure.ItemRepository;
import com.rajat.rent_anything.user.application.TrustGateService;
import com.rajat.rent_anything.user.exceptions.UserTrustGateFailureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private ItemRepository itemRepository;

    @Mock
    private TrustGateService trustGateService;

    private BookingService bookingService;

    private static final Long ITEM_ID = 1L;
    private static final Long RENTER_ID = 2L;
    private static final Long OWNER_ID = 3L;
    private static final Long BOOKING_ID = 100L;
    private static final LocalDate START = LocalDate.of(2026, 6, 1);
    private static final LocalDate END = LocalDate.of(2026, 6, 5);

    @BeforeEach
    void setUp() {
        bookingService = new BookingService(bookingRepository, itemRepository, trustGateService);
    }

    private ItemEntity activeItem() {
        ItemEntity item = new ItemEntity();
        item.setId(ITEM_ID);
        item.setOwnerId(OWNER_ID);
        item.setStatus(ItemStatus.ACTIVE);
        item.setPricePerDay(100.0);
        item.setAvailableFrom(LocalDate.of(2026, 1, 1));
        item.setAvailableTo(LocalDate.of(2026, 12, 31));
        return item;
    }

    private BookingEntity pendingBookingEntity() {
        BookingEntity entity = new BookingEntity();
        entity.setId(BOOKING_ID);
        entity.setItemId(ITEM_ID);
        entity.setRenterId(RENTER_ID);
        entity.setStartDate(START);
        entity.setEndDate(END);
        entity.setAmount(500.0);
        entity.setStatus(BookingStatus.PENDING);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        return entity;
    }

    // ================= createBooking =================

    @Test
    void createBooking_success_returnsPersistedBookingId() {
        CreateBookingCommand command = new CreateBookingCommand(ITEM_ID, RENTER_ID, START, END);
        when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.of(activeItem()));
        when(bookingRepository.findConflictingBookings(eq(ITEM_ID), anyList(), eq(START), eq(END)))
                .thenReturn(List.of());
        when(bookingRepository.save(any(BookingEntity.class))).thenAnswer(invocation -> {
            BookingEntity entity = invocation.getArgument(0);
            entity.setId(BOOKING_ID);
            return entity;
        });

        Long result = bookingService.createBooking(command);

        assertThat(result).isEqualTo(BOOKING_ID);
        verify(trustGateService).ensureUserIsTrusted(RENTER_ID);

        ArgumentCaptor<BookingEntity> captor = ArgumentCaptor.forClass(BookingEntity.class);
        verify(bookingRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(BookingStatus.PENDING);
        assertThat(captor.getValue().getAmount()).isEqualTo(500.0);
    }

    @Test
    void createBooking_untrustedRenter_propagatesTrustGateFailureAndNeverTouchesItem() {
        CreateBookingCommand command = new CreateBookingCommand(ITEM_ID, RENTER_ID, START, END);
        doThrow(new UserTrustGateFailureException(com.rajat.rent_anything.common.enums.ErrorCode.TRUST_GATE_FAILURE))
                .when(trustGateService).ensureUserIsTrusted(RENTER_ID);

        assertThrows(UserTrustGateFailureException.class, () -> bookingService.createBooking(command));

        verify(itemRepository, never()).findById(any());
    }

    @Test
    void createBooking_itemNotFound_throwsItemNotFoundException() {
        CreateBookingCommand command = new CreateBookingCommand(ITEM_ID, RENTER_ID, START, END);
        when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.empty());

        assertThrows(ItemNotFoundException.class, () -> bookingService.createBooking(command));
    }

    @Test
    void createBooking_datesBeforeAvailabilityWindow_throwsInvalidBookingDatesException() {
        ItemEntity item = activeItem();
        item.setAvailableFrom(START.plusDays(1));
        CreateBookingCommand command = new CreateBookingCommand(ITEM_ID, RENTER_ID, START, END);
        when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.of(item));

        assertThrows(InvalidBookingDatesException.class, () -> bookingService.createBooking(command));
    }

    @Test
    void createBooking_datesAfterAvailabilityWindow_throwsInvalidBookingDatesException() {
        ItemEntity item = activeItem();
        item.setAvailableTo(END.minusDays(1));
        CreateBookingCommand command = new CreateBookingCommand(ITEM_ID, RENTER_ID, START, END);
        when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.of(item));

        assertThrows(InvalidBookingDatesException.class, () -> bookingService.createBooking(command));
    }

    @Test
    void createBooking_conflictingBookingExists_throwsBookingConflictException() {
        CreateBookingCommand command = new CreateBookingCommand(ITEM_ID, RENTER_ID, START, END);
        when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.of(activeItem()));
        when(bookingRepository.findConflictingBookings(eq(ITEM_ID), anyList(), eq(START), eq(END)))
                .thenReturn(List.of(pendingBookingEntity()));

        assertThrows(BookingConflictException.class, () -> bookingService.createBooking(command));

        verify(bookingRepository, never()).save(any());
    }

    @Test
    void createBooking_dbExclusionConstraintViolated_translatesToBookingConflictException() {
        CreateBookingCommand command = new CreateBookingCommand(ITEM_ID, RENTER_ID, START, END);
        when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.of(activeItem()));
        when(bookingRepository.findConflictingBookings(eq(ITEM_ID), anyList(), eq(START), eq(END)))
                .thenReturn(List.of());
        when(bookingRepository.save(any(BookingEntity.class)))
                .thenThrow(new DataIntegrityViolationException("exclusion constraint violated"));

        assertThrows(BookingConflictException.class, () -> bookingService.createBooking(command));
    }

    @Test
    void createBooking_inactiveItem_throwsInactiveItemException() {
        ItemEntity item = activeItem();
        item.setStatus(ItemStatus.INACTIVE);
        CreateBookingCommand command = new CreateBookingCommand(ITEM_ID, RENTER_ID, START, END);
        when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.of(item));
        when(bookingRepository.findConflictingBookings(eq(ITEM_ID), anyList(), eq(START), eq(END)))
                .thenReturn(List.of());

        assertThrows(InactiveItemException.class, () -> bookingService.createBooking(command));
    }

    @Test
    void createBooking_renterIsItemOwner_throwsSelfBookingExceptionNotAllowed() {
        ItemEntity item = activeItem();
        item.setOwnerId(RENTER_ID);
        CreateBookingCommand command = new CreateBookingCommand(ITEM_ID, RENTER_ID, START, END);
        when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.of(item));
        when(bookingRepository.findConflictingBookings(eq(ITEM_ID), anyList(), eq(START), eq(END)))
                .thenReturn(List.of());

        assertThrows(SelfBookingExceptionNotAllowed.class, () -> bookingService.createBooking(command));
    }

    // ================= confirmBooking =================

    @Test
    void confirmBooking_callerIsItemOwner_transitionsToConfirmedAndSaves() {
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(pendingBookingEntity()));
        when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.of(activeItem()));

        bookingService.confirmBooking(BOOKING_ID, OWNER_ID);

        ArgumentCaptor<BookingEntity> captor = ArgumentCaptor.forClass(BookingEntity.class);
        verify(bookingRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void confirmBooking_callerIsRenterNotOwner_throwsBookingAccessDeniedException() {
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(pendingBookingEntity()));
        when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.of(activeItem()));

        assertThrows(BookingAccessDeniedException.class,
                () -> bookingService.confirmBooking(BOOKING_ID, RENTER_ID));

        verify(bookingRepository, never()).save(any());
    }

    @Test
    void confirmBooking_callerIsUnrelatedUser_throwsBookingAccessDeniedException() {
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(pendingBookingEntity()));
        when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.of(activeItem()));

        assertThrows(BookingAccessDeniedException.class,
                () -> bookingService.confirmBooking(BOOKING_ID, 999L));
    }

    @Test
    void confirmBooking_bookingDoesNotExist_throwsNoSuchBookingException() {
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.empty());

        assertThrows(NoSuchBookingException.class,
                () -> bookingService.confirmBooking(BOOKING_ID, OWNER_ID));

        verify(itemRepository, never()).findById(any());
    }

    @Test
    void confirmBooking_alreadyConfirmed_throwsInvalidStateActionExceptionFromDomain() {
        BookingEntity confirmed = pendingBookingEntity();
        confirmed.setStatus(BookingStatus.CONFIRMED);
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(confirmed));
        when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.of(activeItem()));

        assertThrows(com.rajat.rent_anything.booking.exceptions.InvalidStateActionException.class,
                () -> bookingService.confirmBooking(BOOKING_ID, OWNER_ID));
    }

    // ================= cancelBooking =================

    @Test
    void cancelBooking_callerIsRenter_transitionsToCancelledAndSaves() {
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(pendingBookingEntity()));
        when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.of(activeItem()));

        bookingService.cancelBooking(BOOKING_ID, RENTER_ID);

        ArgumentCaptor<BookingEntity> captor = ArgumentCaptor.forClass(BookingEntity.class);
        verify(bookingRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    void cancelBooking_callerIsItemOwner_transitionsToCancelledAndSaves() {
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(pendingBookingEntity()));
        when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.of(activeItem()));

        bookingService.cancelBooking(BOOKING_ID, OWNER_ID);

        verify(bookingRepository, times(1)).save(any(BookingEntity.class));
    }

    @Test
    void cancelBooking_callerIsUnrelatedUser_throwsBookingAccessDeniedException() {
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(pendingBookingEntity()));
        when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.of(activeItem()));

        assertThrows(BookingAccessDeniedException.class,
                () -> bookingService.cancelBooking(BOOKING_ID, 999L));

        verify(bookingRepository, never()).save(any());
    }

    @Test
    void cancelBooking_bookingDoesNotExist_throwsNoSuchBookingException() {
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.empty());

        assertThrows(NoSuchBookingException.class,
                () -> bookingService.cancelBooking(BOOKING_ID, RENTER_ID));
    }

    @Test
    void cancelBooking_itemNoLongerExists_throwsItemNotFoundException() {
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(pendingBookingEntity()));
        when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.empty());

        assertThrows(ItemNotFoundException.class,
                () -> bookingService.cancelBooking(BOOKING_ID, RENTER_ID));
    }
}
