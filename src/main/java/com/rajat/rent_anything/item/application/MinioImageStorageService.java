package com.rajat.rent_anything.item.application;

import com.rajat.rent_anything.item.exceptions.ImageStorageException;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Service
@Slf4j
public class MinioImageStorageService implements ImageStorageService {

    private static final String BUCKET_NAME = "rent-anything";
    private static final String MINIO_ENDPOINT = "http://localhost:9000";

    private final MinioClient minioClient;

    public MinioImageStorageService(MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    @Override
    public String upload(Long itemId, MultipartFile file) {

        try {

            String originalFilename = file.getOriginalFilename();

            String imageKey = "items/" + itemId + "/" + UUID.randomUUID() + "-" + originalFilename;

            minioClient.putObject(PutObjectArgs.builder().bucket(BUCKET_NAME).object(imageKey).stream(file.getInputStream(), file.getSize(), -1).contentType(file.getContentType()).build());

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

            minioClient.removeObject(RemoveObjectArgs.builder().bucket(BUCKET_NAME).object(imageKey).build());

            log.info("Deleted image {}", imageKey);

        } catch (Exception ex) {

            log.error("Failed to delete image {}", imageKey, ex);

            throw new ImageStorageException("Failed to delete image from storage");
        }
    }

    @Override
    public String getPublicUrl(String imageKey) {

        return MINIO_ENDPOINT + "/" + BUCKET_NAME + "/" + imageKey;
    }
}