package com.rajat.rent_anything.item.application;

import org.springframework.web.multipart.MultipartFile;

public interface ImageStorageService {

    /**
     * Uploads an image for an item and returns the generated storage key.
     *
     * Example returned key:
     * items/123/8d7a5f6c-camera.jpg
     *
     * The returned key is persisted in the database and can later be
     * converted into a public URL.
     *
     * @param itemId item to which image belongs
     * @param file image file
     * @return storage key
     */
    String upload(Long itemId, MultipartFile file);

    /**
     * Deletes an image from storage.
     *
     * Used when:
     * - Item is deleted
     * - Image is removed
     * - Cleanup jobs run
     *
     * @param imageKey storage key
     */
    void delete(String imageKey);

    /**
     * Generates a publicly accessible URL for an image.
     *
     * Example:
     * https://bucket.s3.amazonaws.com/items/123/image.jpg
     *
     * @param imageKey storage key
     * @return public URL
     */
    String getPublicUrl(String imageKey);
}