package com.rajat.rent_anything.item.api;

import com.rajat.rent_anything.common.model.ApiResponse;
import com.rajat.rent_anything.item.application.ItemImageService;
import com.rajat.rent_anything.item.dto.ItemImageResponseDto;
import com.rajat.rent_anything.security.CustomUserDetails;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/items")
public class ItemImageController {
    private final ItemImageService itemImageService;

    public ItemImageController(ItemImageService itemImageService) {
        this.itemImageService = itemImageService;
    }

    /**
     * Upload images for an item.
     * <p>
     * Business Rules:
     * - Only item owner can upload images.
     * - Maximum 5 images per item.
     * - First uploaded image becomes thumbnail.
     */
    @PostMapping("/{itemId}/images")
    public ResponseEntity<ApiResponse<List<ItemImageResponseDto>>> uploadImages(@PathVariable Long itemId, @RequestParam("files") List<MultipartFile> files, @AuthenticationPrincipal CustomUserDetails customUserDetails) {
        Long userId = customUserDetails.getDomainUser().getId();
        log.info("User {} uploading {} image(s) for item {}", userId, files.size(), itemId);
        List<ItemImageResponseDto> uploadedImages = itemImageService.uploadImages(itemId, userId, files);
        return ResponseEntity.ok(ApiResponse.success(uploadedImages));
    }

    /**
     * Returns all images belonging to an item.
     */
    @GetMapping("/{itemId}/images")
    public ResponseEntity<ApiResponse<List<ItemImageResponseDto>>> getItemImages(@PathVariable Long itemId) {
        List<ItemImageResponseDto> images = itemImageService.getItemImages(itemId);
        return ResponseEntity.ok(ApiResponse.success(images));
    }

    /**
     * Returns thumbnail image for an item.
     */
    @GetMapping("/{itemId}/thumbnail")
    public ResponseEntity<ApiResponse<ItemImageResponseDto>> getThumbnail(@PathVariable Long itemId) {
        ItemImageResponseDto thumbnail = itemImageService.getThumbnail(itemId);
        return ResponseEntity.ok(ApiResponse.success(thumbnail));
    }

    /**
     * Deletes an image.
     * <p>
     * Only the item owner can delete images.
     */
    @DeleteMapping("/images/{imageId}")
    public ResponseEntity<ApiResponse<Void>> deleteImage(@PathVariable Long imageId, @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long userId = userDetails.getDomainUser().getId();
        log.info("User {} deleting image {}", userId, imageId);
        itemImageService.deleteImage(imageId, userId);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
