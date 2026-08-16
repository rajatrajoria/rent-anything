package com.rajat.rent_anything.item.application;

import com.rajat.rent_anything.item.dto.ItemImageResponseDto;
import com.rajat.rent_anything.item.exceptions.ItemNotFoundException;
import com.rajat.rent_anything.item.infrastructure.ItemEntity;
import com.rajat.rent_anything.item.infrastructure.ItemImageEntity;
import com.rajat.rent_anything.item.infrastructure.ItemImageRepository;
import com.rajat.rent_anything.item.infrastructure.ItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ItemImageServiceTest {

    @Mock
    private ItemRepository itemRepository;

    @Mock
    private ItemImageRepository itemImageRepository;

    @Mock
    private ImageStorageService imageStorageService;

    private ItemImageService itemImageService;

    private static final Long ITEM_ID = 1L;
    private static final Long OWNER_ID = 2L;

    @BeforeEach
    void setUp() {
        itemImageService = new ItemImageService(itemRepository, itemImageRepository, imageStorageService);
    }

    private ItemEntity ownedItem() {
        ItemEntity item = new ItemEntity();
        item.setId(ITEM_ID);
        item.setOwnerId(OWNER_ID);
        return item;
    }

    private MultipartFile validImageFile(String name) {
        return new MockMultipartFile(name, name + ".jpg", "image/jpeg", new byte[]{1, 2, 3});
    }

    private ItemImageEntity imageEntity(Long id, boolean thumbnail) {
        ItemImageEntity entity = new ItemImageEntity();
        entity.setId(id);
        entity.setItemId(ITEM_ID);
        entity.setImageKey("items/" + ITEM_ID + "/" + id + ".jpg");
        entity.setThumbnail(thumbnail);
        entity.setDisplayOrder(id.intValue());
        return entity;
    }

    // ================= uploadImages =================

    @Test
    void uploadImages_emptyFileList_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> itemImageService.uploadImages(ITEM_ID, OWNER_ID, List.of()));
        verify(itemRepository, never()).findById(any());
    }

    @Test
    void uploadImages_nullFileList_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> itemImageService.uploadImages(ITEM_ID, OWNER_ID, null));
    }

    @Test
    void uploadImages_itemDoesNotExist_throwsItemNotFoundException() {
        when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.empty());

        assertThrows(ItemNotFoundException.class,
                () -> itemImageService.uploadImages(ITEM_ID, OWNER_ID, List.of(validImageFile("a"))));
    }

    @Test
    void uploadImages_callerIsNotOwner_throwsIllegalStateException() {
        when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.of(ownedItem()));

        assertThrows(IllegalStateException.class,
                () -> itemImageService.uploadImages(ITEM_ID, 999L, List.of(validImageFile("a"))));

        verify(itemImageRepository, never()).countByItemId(any());
    }

    @Test
    void uploadImages_exceedsMaxOfFiveImages_throwsIllegalArgumentException() {
        when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.of(ownedItem()));
        when(itemImageRepository.countByItemId(ITEM_ID)).thenReturn(4L);

        List<MultipartFile> twoNewFiles = List.of(validImageFile("a"), validImageFile("b"));

        assertThrows(IllegalArgumentException.class,
                () -> itemImageService.uploadImages(ITEM_ID, OWNER_ID, twoNewFiles));

        verify(imageStorageService, never()).upload(any(), any());
    }

    @Test
    void uploadImages_firstImageForItem_becomesThumbnail() {
        when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.of(ownedItem()));
        when(itemImageRepository.countByItemId(ITEM_ID)).thenReturn(0L);
        when(itemImageRepository.findByItemIdOrderByDisplayOrderAsc(ITEM_ID)).thenReturn(new ArrayList<>());
        when(imageStorageService.upload(eq(ITEM_ID), any())).thenReturn("items/1/key.jpg");
        when(itemImageRepository.save(any(ItemImageEntity.class))).thenAnswer(inv -> {
            ItemImageEntity e = inv.getArgument(0);
            e.setId(50L);
            return e;
        });
        when(imageStorageService.getImageUrl(anyString())).thenReturn("http://img/key.jpg");

        List<ItemImageResponseDto> result = itemImageService.uploadImages(ITEM_ID, OWNER_ID, List.of(validImageFile("a")));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).thumbnail()).isTrue();

        ArgumentCaptor<ItemImageEntity> captor = ArgumentCaptor.forClass(ItemImageEntity.class);
        verify(itemImageRepository).save(captor.capture());
        assertThat(captor.getValue().isThumbnail()).isTrue();
        assertThat(captor.getValue().getDisplayOrder()).isEqualTo(1);
    }

    @Test
    void uploadImages_thumbnailAlreadyExists_newImagesAreNotThumbnail() {
        when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.of(ownedItem()));
        when(itemImageRepository.countByItemId(ITEM_ID)).thenReturn(1L);
        when(itemImageRepository.findByItemIdOrderByDisplayOrderAsc(ITEM_ID))
                .thenReturn(new ArrayList<>(List.of(imageEntity(1L, true))));
        when(imageStorageService.upload(eq(ITEM_ID), any())).thenReturn("items/1/key2.jpg");
        when(itemImageRepository.save(any(ItemImageEntity.class))).thenAnswer(inv -> {
            ItemImageEntity e = inv.getArgument(0);
            e.setId(51L);
            return e;
        });
        when(imageStorageService.getImageUrl(anyString())).thenReturn("http://img/key2.jpg");

        List<ItemImageResponseDto> result = itemImageService.uploadImages(ITEM_ID, OWNER_ID, List.of(validImageFile("b")));

        assertThat(result.get(0).thumbnail()).isFalse();

        ArgumentCaptor<ItemImageEntity> captor = ArgumentCaptor.forClass(ItemImageEntity.class);
        verify(itemImageRepository).save(captor.capture());
        assertThat(captor.getValue().isThumbnail()).isFalse();
        assertThat(captor.getValue().getDisplayOrder()).isEqualTo(2);
    }

    @Test
    void uploadImages_unsupportedContentType_throwsIllegalArgumentException() {
        when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.of(ownedItem()));
        when(itemImageRepository.countByItemId(ITEM_ID)).thenReturn(0L);
        when(itemImageRepository.findByItemIdOrderByDisplayOrderAsc(ITEM_ID)).thenReturn(new ArrayList<>());

        MultipartFile badFile = new MockMultipartFile("a", "a.pdf", "application/pdf", new byte[]{1});

        assertThrows(IllegalArgumentException.class,
                () -> itemImageService.uploadImages(ITEM_ID, OWNER_ID, List.of(badFile)));

        verify(imageStorageService, never()).upload(any(), any());
    }

    @Test
    void uploadImages_fileTooLarge_throwsIllegalArgumentException() {
        when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.of(ownedItem()));
        when(itemImageRepository.countByItemId(ITEM_ID)).thenReturn(0L);
        when(itemImageRepository.findByItemIdOrderByDisplayOrderAsc(ITEM_ID)).thenReturn(new ArrayList<>());

        byte[] tooBig = new byte[11 * 1024 * 1024];
        MultipartFile bigFile = new MockMultipartFile("a", "a.jpg", "image/jpeg", tooBig);

        assertThrows(IllegalArgumentException.class,
                () -> itemImageService.uploadImages(ITEM_ID, OWNER_ID, List.of(bigFile)));
    }

    @Test
    void uploadImages_emptyFile_throwsIllegalArgumentException() {
        when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.of(ownedItem()));
        when(itemImageRepository.countByItemId(ITEM_ID)).thenReturn(0L);
        when(itemImageRepository.findByItemIdOrderByDisplayOrderAsc(ITEM_ID)).thenReturn(new ArrayList<>());

        MultipartFile empty = new MockMultipartFile("a", "a.jpg", "image/jpeg", new byte[]{});

        assertThrows(IllegalArgumentException.class,
                () -> itemImageService.uploadImages(ITEM_ID, OWNER_ID, List.of(empty)));
    }

    // ================= getItemImages / getThumbnail =================

    @Test
    void getItemImages_returnsMappedResponseDtos() {
        when(itemImageRepository.findByItemIdOrderByDisplayOrderAsc(ITEM_ID))
                .thenReturn(List.of(imageEntity(1L, true), imageEntity(2L, false)));
        when(imageStorageService.getImageUrl(anyString())).thenReturn("http://img/x.jpg");

        List<ItemImageResponseDto> result = itemImageService.getItemImages(ITEM_ID);

        assertThat(result).hasSize(2);
    }

    @Test
    void getThumbnail_present_returnsDto() {
        when(itemImageRepository.findByItemIdAndIsThumbnailTrue(ITEM_ID))
                .thenReturn(Optional.of(imageEntity(1L, true)));
        when(imageStorageService.getImageUrl(anyString())).thenReturn("http://img/x.jpg");

        ItemImageResponseDto dto = itemImageService.getThumbnail(ITEM_ID);

        assertThat(dto).isNotNull();
        assertThat(dto.thumbnail()).isTrue();
    }

    @Test
    void getThumbnail_absent_returnsNull() {
        when(itemImageRepository.findByItemIdAndIsThumbnailTrue(ITEM_ID)).thenReturn(Optional.empty());

        assertThat(itemImageService.getThumbnail(ITEM_ID)).isNull();
    }

    @Test
    void getThumbnailsByItemIds_emptyInput_returnsEmptyMapWithoutQuerying() {
        Map<Long, ItemImageResponseDto> result = itemImageService.getThumbnailsByItemIds(List.of());

        assertThat(result).isEmpty();
        verify(itemImageRepository, never()).findByItemIdInAndIsThumbnailTrue(any());
    }

    @Test
    void getThumbnailsByItemIds_nonEmptyInput_returnsMapKeyedByItemId() {
        when(itemImageRepository.findByItemIdInAndIsThumbnailTrue(List.of(1L, 2L)))
                .thenReturn(List.of(imageEntity(1L, true)));
        when(imageStorageService.getImageUrl(anyString())).thenReturn("http://img/x.jpg");

        Map<Long, ItemImageResponseDto> result = itemImageService.getThumbnailsByItemIds(List.of(1L, 2L));

        assertThat(result).containsOnlyKeys(ITEM_ID);
    }

    // ================= deleteImage =================

    @Test
    void deleteImage_nonThumbnailImage_deletesWithoutReassignment() {
        ItemImageEntity image = imageEntity(2L, false);
        when(itemImageRepository.findById(2L)).thenReturn(Optional.of(image));
        when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.of(ownedItem()));

        itemImageService.deleteImage(2L, OWNER_ID);

        verify(imageStorageService).delete(image.getImageKey());
        verify(itemImageRepository).delete(image);
        verify(itemImageRepository, never()).findByItemIdOrderByDisplayOrderAsc(any());
    }

    @Test
    void deleteImage_thumbnailImageWithRemainingImages_promotesNextImageToThumbnail() {
        ItemImageEntity thumbnail = imageEntity(1L, true);
        ItemImageEntity remaining = imageEntity(2L, false);
        when(itemImageRepository.findById(1L)).thenReturn(Optional.of(thumbnail));
        when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.of(ownedItem()));
        when(itemImageRepository.findByItemIdOrderByDisplayOrderAsc(ITEM_ID)).thenReturn(List.of(remaining));

        itemImageService.deleteImage(1L, OWNER_ID);

        ArgumentCaptor<ItemImageEntity> captor = ArgumentCaptor.forClass(ItemImageEntity.class);
        verify(itemImageRepository).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(2L);
        assertThat(captor.getValue().isThumbnail()).isTrue();
    }

    @Test
    void deleteImage_thumbnailImageWithNoRemainingImages_doesNotReassign() {
        ItemImageEntity thumbnail = imageEntity(1L, true);
        when(itemImageRepository.findById(1L)).thenReturn(Optional.of(thumbnail));
        when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.of(ownedItem()));
        when(itemImageRepository.findByItemIdOrderByDisplayOrderAsc(ITEM_ID)).thenReturn(List.of());

        itemImageService.deleteImage(1L, OWNER_ID);

        verify(itemImageRepository, never()).save(any());
    }

    @Test
    void deleteImage_imageDoesNotExist_throwsIllegalArgumentException() {
        when(itemImageRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> itemImageService.deleteImage(1L, OWNER_ID));
    }

    @Test
    void deleteImage_itemNoLongerExists_throwsItemNotFoundException() {
        ItemImageEntity image = imageEntity(1L, false);
        when(itemImageRepository.findById(1L)).thenReturn(Optional.of(image));
        when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.empty());

        assertThrows(ItemNotFoundException.class, () -> itemImageService.deleteImage(1L, OWNER_ID));
    }

    @Test
    void deleteImage_callerIsNotOwner_throwsIllegalStateException() {
        ItemImageEntity image = imageEntity(1L, false);
        when(itemImageRepository.findById(1L)).thenReturn(Optional.of(image));
        when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.of(ownedItem()));

        assertThrows(IllegalStateException.class, () -> itemImageService.deleteImage(1L, 999L));

        verify(imageStorageService, never()).delete(any());
    }

    // ================= getImageCount =================

    @Test
    void getImageCount_delegatesToRepository() {
        when(itemImageRepository.countByItemId(ITEM_ID)).thenReturn(3L);

        assertThat(itemImageService.getImageCount(ITEM_ID)).isEqualTo(3L);
    }
}
