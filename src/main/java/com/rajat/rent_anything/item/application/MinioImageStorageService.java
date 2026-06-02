package com.rajat.rent_anything.item.application;

import com.rajat.rent_anything.item.config.StorageProperties;
import com.rajat.rent_anything.item.exceptions.ImageStorageException;
import io.minio.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Service
@Slf4j
public class MinioImageStorageService implements ImageStorageService {

    private final MinioClient minioClient;
    private final StorageProperties storageProperties;

    public MinioImageStorageService(
            MinioClient minioClient,
            StorageProperties storageProperties
    ) {
        this.minioClient = minioClient;
        this.storageProperties = storageProperties;
    }

    @Override
    public String upload(Long itemId, MultipartFile file) {

        try {

            String originalFilename = file.getOriginalFilename();

            String imageKey =
                    "items/"
                            + itemId
                            + "/"
                            + UUID.randomUUID()
                            + "-"
                            + originalFilename;

            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(storageProperties.getBucketName())
                            .object(imageKey)
                            .stream(
                                    file.getInputStream(),
                                    file.getSize(),
                                    -1
                            )
                            .contentType(file.getContentType())
                            .build()
            );

            log.info(
                    "Uploaded image for itemId {} with key {}",
                    itemId,
                    imageKey
            );

            return imageKey;

        } catch (Exception ex) {
            log.error(
                    "Failed to upload image for itemId {}",
                    itemId,
                    ex
            );
            throw new ImageStorageException(
                    "Failed to upload image to storage"
            );
        }
    }

    @Override
    public void delete(String imageKey) {

        try {

            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(storageProperties.getBucketName())
                            .object(imageKey)
                            .build()
            );

            log.info("Deleted image {}", imageKey);

        } catch (Exception ex) {
            log.error(
                    "Failed to delete image {}",
                    imageKey,
                    ex
            );

            throw new ImageStorageException(
                    "Failed to delete image from storage"
            );
        }
    }

    @Override
    public String getPublicUrl(String imageKey) {

        return storageProperties.getEndpoint()
                + "/"
                + storageProperties.getBucketName()
                + "/"
                + imageKey;
    }
}