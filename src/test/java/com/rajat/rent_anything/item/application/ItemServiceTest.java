package com.rajat.rent_anything.item.application;

import com.rajat.rent_anything.booking.domain.BookingStatus;
import com.rajat.rent_anything.booking.infrastructure.BookingEntity;
import com.rajat.rent_anything.booking.infrastructure.BookingRepository;
import com.rajat.rent_anything.item.application.commands.CreateItemCommand;
import com.rajat.rent_anything.item.domain.ItemStatus;
import com.rajat.rent_anything.item.dto.ItemDetailsResponseDto;
import com.rajat.rent_anything.item.dto.ItemImageResponseDto;
import com.rajat.rent_anything.item.dto.ItemSearchResponseDto;
import com.rajat.rent_anything.item.exceptions.IllegalItemModificationException;
import com.rajat.rent_anything.item.exceptions.InvalidItemException;
import com.rajat.rent_anything.item.exceptions.ItemNotFoundException;
import com.rajat.rent_anything.item.infrastructure.ItemEntity;
import com.rajat.rent_anything.item.infrastructure.ItemRepository;
import com.rajat.rent_anything.item.infrastructure.ItemSearchRow;
import com.rajat.rent_anything.user.application.TrustGateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ItemServiceTest {

    @Mock
    private ItemRepository itemRepository;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private TrustGateService trustGateService;

    @Mock
    private ItemImageService itemImageService;

    private ItemService itemService;

    private static final Long ITEM_ID = 1L;
    private static final Long OWNER_ID = 2L;
    private static final LocalDate FROM = LocalDate.of(2026, 1, 1);
    private static final LocalDate TO = LocalDate.of(2026, 12, 31);

    @BeforeEach
    void setUp() {
        itemService = new ItemService(itemRepository, bookingRepository, trustGateService, itemImageService);
    }

    private ItemEntity itemEntity(ItemStatus status) {
        ItemEntity entity = new ItemEntity();
        entity.setId(ITEM_ID);
        entity.setOwnerId(OWNER_ID);
        entity.setCategoryId(5L);
        entity.setTitle("Camera");
        entity.setDescription("Nice camera");
        entity.setPricePerDay(100.0);
        entity.setDepositAmount(50.0);
        entity.setStatus(status);
        entity.setAvailableFrom(FROM);
        entity.setAvailableTo(TO);
        return entity;
    }

    private CreateItemCommand createItemCommand() {
        return new CreateItemCommand(5L, "Camera", "Nice camera", 100.0, 50.0, FROM, TO, 12.0, 34.0);
    }

    // ================= createItem =================

    @Test
    void createItem_success_returnsGeneratedIdAndStartsInactive() {
        when(itemRepository.save(any(ItemEntity.class))).thenAnswer(invocation -> {
            ItemEntity entity = invocation.getArgument(0);
            entity.setId(ITEM_ID);
            return entity;
        });

        Long result = itemService.createItem(createItemCommand(), OWNER_ID);

        assertThat(result).isEqualTo(ITEM_ID);
        verify(trustGateService).ensureUserIsTrusted(OWNER_ID);

        ArgumentCaptor<ItemEntity> captor = ArgumentCaptor.forClass(ItemEntity.class);
        verify(itemRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ItemStatus.INACTIVE);
        assertThat(captor.getValue().getOwnerId()).isEqualTo(OWNER_ID);
    }

    // ================= activateItem =================

    @Test
    void activateItem_ownerWithEnoughImages_activatesItem() {
        when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.of(itemEntity(ItemStatus.INACTIVE)));
        when(itemImageService.getImageCount(ITEM_ID)).thenReturn(2L);
        when(itemRepository.save(any(ItemEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        itemService.activateItem(ITEM_ID, OWNER_ID);

        ArgumentCaptor<ItemEntity> captor = ArgumentCaptor.forClass(ItemEntity.class);
        verify(itemRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ItemStatus.ACTIVE);
    }

    @Test
    void activateItem_fewerThanTwoImages_throwsIllegalStateException() {
        when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.of(itemEntity(ItemStatus.INACTIVE)));
        when(itemImageService.getImageCount(ITEM_ID)).thenReturn(1L);

        assertThrows(IllegalStateException.class, () -> itemService.activateItem(ITEM_ID, OWNER_ID));

        verify(itemRepository, never()).save(any());
    }

    @Test
    void activateItem_callerIsNotOwner_throwsIllegalItemModificationException() {
        when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.of(itemEntity(ItemStatus.INACTIVE)));

        assertThrows(IllegalItemModificationException.class,
                () -> itemService.activateItem(ITEM_ID, 999L));

        verify(itemImageService, never()).getImageCount(any());
    }

    @Test
    void activateItem_itemDoesNotExist_throwsItemNotFoundException() {
        when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.empty());

        assertThrows(ItemNotFoundException.class, () -> itemService.activateItem(ITEM_ID, OWNER_ID));
    }

    // ================= deactivateItem =================

    @Test
    void deactivateItem_owner_setsStatusInactive() {
        when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.of(itemEntity(ItemStatus.ACTIVE)));
        when(itemRepository.save(any(ItemEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        itemService.deactivateItem(ITEM_ID, OWNER_ID);

        ArgumentCaptor<ItemEntity> captor = ArgumentCaptor.forClass(ItemEntity.class);
        verify(itemRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ItemStatus.INACTIVE);
    }

    @Test
    void deactivateItem_callerIsNotOwner_throwsIllegalItemModificationException() {
        when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.of(itemEntity(ItemStatus.ACTIVE)));

        assertThrows(IllegalItemModificationException.class,
                () -> itemService.deactivateItem(ITEM_ID, 999L));
    }

    // ================= updatePrice =================

    @Test
    void updatePrice_validPrice_updatesItem() {
        when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.of(itemEntity(ItemStatus.ACTIVE)));
        when(itemRepository.save(any(ItemEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        itemService.updatePrice(ITEM_ID, 250.0, OWNER_ID);

        ArgumentCaptor<ItemEntity> captor = ArgumentCaptor.forClass(ItemEntity.class);
        verify(itemRepository).save(captor.capture());
        assertThat(captor.getValue().getPricePerDay()).isEqualTo(250.0);
    }

    @Test
    void updatePrice_zeroOrNegativePrice_throwsInvalidItemException() {
        when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.of(itemEntity(ItemStatus.ACTIVE)));

        assertThrows(InvalidItemException.class, () -> itemService.updatePrice(ITEM_ID, 0.0, OWNER_ID));
    }

    @Test
    void updatePrice_callerIsNotOwner_throwsIllegalItemModificationException() {
        when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.of(itemEntity(ItemStatus.ACTIVE)));

        assertThrows(IllegalItemModificationException.class,
                () -> itemService.updatePrice(ITEM_ID, 250.0, 999L));
    }

    // ================= updateAvailability =================

    @Test
    void updateAvailability_noConflictingBookings_updatesWindow() {
        when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.of(itemEntity(ItemStatus.ACTIVE)));
        when(bookingRepository.findByItemIdAndStatusIn(eq(ITEM_ID), any())).thenReturn(List.of());

        LocalDate newFrom = FROM.plusDays(10);
        LocalDate newTo = TO.minusDays(10);
        itemService.updateAvailability(ITEM_ID, newFrom, newTo, OWNER_ID);

        ArgumentCaptor<ItemEntity> captor = ArgumentCaptor.forClass(ItemEntity.class);
        verify(itemRepository).save(captor.capture());
        assertThat(captor.getValue().getAvailableFrom()).isEqualTo(newFrom);
        assertThat(captor.getValue().getAvailableTo()).isEqualTo(newTo);
    }

    @Test
    void updateAvailability_shrinkingBreaksActiveBooking_throwsInvalidItemException() {
        when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.of(itemEntity(ItemStatus.ACTIVE)));

        BookingEntity activeBooking = new BookingEntity();
        activeBooking.setStartDate(FROM);
        activeBooking.setEndDate(FROM.plusDays(5));
        activeBooking.setStatus(BookingStatus.CONFIRMED);
        when(bookingRepository.findByItemIdAndStatusIn(eq(ITEM_ID), any())).thenReturn(List.of(activeBooking));

        LocalDate newFrom = FROM.plusDays(1); // now excludes the active booking's start date
        assertThrows(InvalidItemException.class,
                () -> itemService.updateAvailability(ITEM_ID, newFrom, TO, OWNER_ID));

        verify(itemRepository, never()).save(any());
    }

    @Test
    void updateAvailability_callerIsNotOwner_throwsIllegalItemModificationException() {
        when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.of(itemEntity(ItemStatus.ACTIVE)));

        assertThrows(IllegalItemModificationException.class,
                () -> itemService.updateAvailability(ITEM_ID, FROM, TO, 999L));
    }

    // ================= search =================

    @Test
    void search_mapsRowsAndAttachesThumbnailsInSingleBatchLookup() {
        ItemSearchRow row = mock(ItemSearchRow.class);
        when(row.getItemId()).thenReturn(ITEM_ID);
        when(row.getOwnerId()).thenReturn(OWNER_ID);
        when(row.getTitle()).thenReturn("Camera");
        when(row.getDescription()).thenReturn("Nice camera");
        when(row.getPricePerDay()).thenReturn(100.0);
        when(row.getDistance()).thenReturn(2500.0); // meters
        when(row.getTextScore()).thenReturn(0.8);

        when(itemRepository.searchAvailableItemsWithinRadiusAndWithKeywords(
                anyDouble(), anyDouble(), anyDouble(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(row));

        ItemImageResponseDto thumbnail = new ItemImageResponseDto(1L, "http://img/thumb.jpg", true, 1);
        when(itemImageService.getThumbnailsByItemIds(List.of(ITEM_ID)))
                .thenReturn(Map.of(ITEM_ID, thumbnail));

        List<ItemSearchResponseDto> results = itemService.searchAvailableItemsWithKeywordAndWithinGivenLocation(
                12.0, 34.0, 5.0, FROM, TO, "camera", 10, 0);

        assertThat(results).hasSize(1);
        ItemSearchResponseDto dto = results.get(0);
        assertThat(dto.itemId()).isEqualTo(ITEM_ID);
        assertThat(dto.distance()).isEqualTo(2.5); // converted meters -> km
        assertThat(dto.thumbnailUrl()).isEqualTo("http://img/thumb.jpg");

        verify(itemImageService, never()).getThumbnail(any());
    }

    @Test
    void search_itemWithoutThumbnail_returnsNullThumbnailUrl() {
        ItemSearchRow row = mock(ItemSearchRow.class);
        when(row.getItemId()).thenReturn(ITEM_ID);
        when(row.getOwnerId()).thenReturn(OWNER_ID);
        when(row.getTitle()).thenReturn("Camera");
        when(row.getDescription()).thenReturn("Nice camera");
        when(row.getPricePerDay()).thenReturn(100.0);
        when(row.getDistance()).thenReturn(1000.0);
        when(row.getTextScore()).thenReturn(0.5);

        when(itemRepository.searchAvailableItemsWithinRadiusAndWithKeywords(
                anyDouble(), anyDouble(), anyDouble(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(row));
        when(itemImageService.getThumbnailsByItemIds(List.of(ITEM_ID))).thenReturn(Map.of());

        List<ItemSearchResponseDto> results = itemService.searchAvailableItemsWithKeywordAndWithinGivenLocation(
                12.0, 34.0, 5.0, FROM, TO, null, 10, 0);

        assertThat(results.get(0).thumbnailUrl()).isNull();
    }

    // ================= getItemDetails =================

    @Test
    void getItemDetails_existingItem_returnsDetailsWithThumbnailAndImages() {
        when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.of(itemEntity(ItemStatus.ACTIVE)));
        ItemImageResponseDto thumbnail = new ItemImageResponseDto(1L, "http://img/thumb.jpg", true, 1);
        when(itemImageService.getThumbnail(ITEM_ID)).thenReturn(thumbnail);
        when(itemImageService.getItemImages(ITEM_ID)).thenReturn(List.of(thumbnail));

        ItemDetailsResponseDto dto = itemService.getItemDetails(ITEM_ID);

        assertThat(dto.id()).isEqualTo(ITEM_ID);
        assertThat(dto.status()).isEqualTo(ItemStatus.ACTIVE.name());
        assertThat(dto.thumbnailUrl()).isEqualTo("http://img/thumb.jpg");
        assertThat(dto.images()).hasSize(1);
    }

    @Test
    void getItemDetails_itemDoesNotExist_throwsItemNotFoundException() {
        when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.empty());

        assertThrows(ItemNotFoundException.class, () -> itemService.getItemDetails(ITEM_ID));
    }
}
