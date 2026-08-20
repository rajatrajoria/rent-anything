package com.rajat.rent_anything.item.application;

import com.rajat.rent_anything.item.exceptions.ImageStorageException;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class MinioImageStorageService implements ImageStorageService {

    private static final Map<String, String> EXTENSIONS_BY_CONTENT_TYPE = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp"
    );

    private final MinioClient minioClient;
    private final String bucketName;

    public MinioImageStorageService(MinioClient minioClient, @Value("${minio.bucket-name}") String bucketName) {
        this.minioClient = minioClient;
        this.bucketName = bucketName;
    }

    @Override
    public String upload(Long itemId, MultipartFile file) {

        try {

            String extension = EXTENSIONS_BY_CONTENT_TYPE.getOrDefault(file.getContentType(), "");

            String imageKey = "items/" + itemId + "/" + UUID.randomUUID() + extension;

            minioClient.putObject(PutObjectArgs.builder().bucket(bucketName).object(imageKey).stream(file.getInputStream(), file.getSize(), -1).contentType(file.getContentType()).build());

            log.info("Uploaded image for itemId {} with key {}", itemId, imageKey);

            return imageKey;

        } catch (Exception ex) {

            log.error("Failed to upload image for itemId {}", itemId, ex);

            throw new ImageStorageException("Failed to upload image to storage");
        }
    }

    @Override
    public void delete(String imageKey) {

        try {

            minioClient.removeObject(RemoveObjectArgs.builder().bucket(bucketName).object(imageKey).build());

            log.info("Deleted image {}", imageKey);

        } catch (Exception ex) {

            log.error("Failed to delete image {}", imageKey, ex);

            throw new ImageStorageException("Failed to delete image from storage");
        }
    }

    @Override
    public String getImageUrl(String imageKey) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucketName)
                            .object(imageKey)
                            .expiry(60 * 60)
                            .build()
            );
        } catch (Exception ex) {
            log.error("Failed to generate URL for image {}", imageKey, ex);
            throw new ImageStorageException("Failed to generate image URL");
        }
    }
}