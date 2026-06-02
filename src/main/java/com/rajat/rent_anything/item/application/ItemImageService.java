package com.rajat.rent_anything.item.application;

import com.rajat.rent_anything.item.dto.ItemImageResponseDto;
import com.rajat.rent_anything.item.exceptions.ItemNotFoundException;
import com.rajat.rent_anything.item.infrastructure.ItemEntity;
import com.rajat.rent_anything.item.infrastructure.ItemImageEntity;
import com.rajat.rent_anything.item.infrastructure.ItemImageRepository;
import com.rajat.rent_anything.item.infrastructure.ItemRepository;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
@Transactional
public class ItemImageService {

    private static final int MAX_IMAGES_PER_ITEM = 5;
    private static final long MAX_IMAGE_SIZE_BYTES = 10 * 1024 * 1024;

    private static final List<String> SUPPORTED_CONTENT_TYPES =
            List.of(
                    "image/jpeg",
                    "image/png",
                    "image/webp"
            );

    private final ItemRepository itemRepository;
    private final ItemImageRepository itemImageRepository;
    private final ImageStorageService imageStorageService;

    public ItemImageService(
            ItemRepository itemRepository,
            ItemImageRepository itemImageRepository,
            ImageStorageService imageStorageService
    ) {
        this.itemRepository = itemRepository;
        this.itemImageRepository = itemImageRepository;
        this.imageStorageService = imageStorageService;
    }

    /**
     * Uploads images for an item.
     *
     * Business Rules:
     * - Item must exist.
     * - User must own the item.
     * - Total images cannot exceed 5.
     * - First uploaded image becomes thumbnail.
     * - Only supported image formats allowed.
     * - Max image size 10MB.
     */
    public void uploadImages(
            Long itemId,
            Long userId,
            List<MultipartFile> files
    ) {

        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one image is required"
            );
        }

        ItemEntity item = itemRepository.findById(itemId)
                .orElseThrow(() ->
                        new ItemNotFoundException(
                                "Item not found"
                        )
                );

        validateOwnership(item, userId);

        long existingImageCount = itemImageRepository.countByItemId(itemId);

        if (existingImageCount + files.size() > MAX_IMAGES_PER_ITEM) {
            throw new IllegalArgumentException(
                    "Maximum 5 images allowed per item"
            );
        }

        List<ItemImageEntity> existingImages = itemImageRepository.findByItemIdOrderByDisplayOrderAsc(itemId);

        int nextDisplayOrder = existingImages.size() + 1;

        boolean thumbnailExists = existingImages.stream().anyMatch(ItemImageEntity::isThumbnail);

        for (MultipartFile file : files) {
            validateImage(file);

            String imageKey = imageStorageService.upload(itemId, file);

            ItemImageEntity image = new ItemImageEntity();
            image.setItemId(itemId);
            image.setImageKey(imageKey);
            image.setDisplayOrder(nextDisplayOrder++);
            image.setCreatedAt(LocalDateTime.now());

            if (!thumbnailExists) {
                image.setThumbnail(true);
                thumbnailExists = true;
            } else {
                image.setThumbnail(false);
            }

            itemImageRepository.save(image);

            log.info("Uploaded image for itemId {} with key {}", itemId, imageKey);
        }
    }

    /**
     * Retrieves all images for an item.
     */
    @Transactional
    public List<ItemImageResponseDto> getItemImages(Long itemId) {

        return itemImageRepository
                .findByItemIdOrderByDisplayOrderAsc(itemId)
                .stream()
                .map(this::toResponseDto)
                .toList();
    }

    /**
     * Retrieves thumbnail image.
     */
    @Transactional
    public ItemImageResponseDto getThumbnail(Long itemId) {

        return itemImageRepository
                .findByItemIdAndIsThumbnailTrue(itemId)
                .map(this::toResponseDto)
                .orElse(null);
    }

    /**
     * Deletes an image.
     *
     * If thumbnail is deleted,
     * the next image becomes thumbnail.
     */
    public void deleteImage(
            Long imageId,
            Long userId
    ) {

        ItemImageEntity image =
                itemImageRepository.findById(imageId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Image not found"
                                )
                        );

        ItemEntity item =
                itemRepository.findById(image.getItemId())
                        .orElseThrow(() ->
                                new ItemNotFoundException(
                                        "Item not found"
                                )
                        );

        validateOwnership(item, userId);

        boolean deletedThumbnail =
                image.isThumbnail();

        imageStorageService.delete(
                image.getImageKey()
        );

        itemImageRepository.delete(image);

        if (deletedThumbnail) {

            List<ItemImageEntity> remainingImages =
                    itemImageRepository
                            .findByItemIdOrderByDisplayOrderAsc(
                                    item.getId()
                            );

            if (!remainingImages.isEmpty()) {

                ItemImageEntity newThumbnail =
                        remainingImages.get(0);

                newThumbnail.setThumbnail(true);

                itemImageRepository.save(newThumbnail);
            }
        }

        log.info(
                "Deleted image {} for item {}",
                imageId,
                item.getId()
        );
    }

    /**
     * Returns image count.
     *
     * Useful during item activation
     * to verify minimum image requirements.
     */
    public long getImageCount(Long itemId) {
        return itemImageRepository.countByItemId(itemId);
    }

    private void validateImage(
            MultipartFile file
    ) {

        if (file.isEmpty()) {
            throw new IllegalArgumentException(
                    "Image file is empty"
            );
        }

        if (file.getSize()
                > MAX_IMAGE_SIZE_BYTES) {

            throw new IllegalArgumentException(
                    "Maximum image size is 10MB"
            );
        }

        String contentType =
                file.getContentType();

        if (contentType == null
                || !SUPPORTED_CONTENT_TYPES.contains(
                contentType
        )) {

            throw new IllegalArgumentException(
                    "Only JPEG, PNG and WEBP images are supported"
            );
        }
    }

    private void validateOwnership(ItemEntity item, Long userId) {
        if (!item.getOwnerId().equals(userId)) {
            throw new IllegalStateException(
                    "Only item owner can manage images"
            );
        }
    }

    private ItemImageResponseDto toResponseDto(ItemImageEntity entity) {
        return new ItemImageResponseDto(
                entity.getId(),
                imageStorageService.getPublicUrl(
                        entity.getImageKey()
                ),
                entity.isThumbnail(),
                entity.getDisplayOrder()
        );
    }
}