package com.rajat.rent_anything.kyc.application;

import com.rajat.rent_anything.kyc.exceptions.KycDocumentStorageException;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class MinioKycDocumentStorageService implements KycDocumentStorageService {

    private static final String BUCKET_NAME = "rent-anything";

    private static final Map<String, String> EXTENSIONS_BY_CONTENT_TYPE = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp"
    );

    private final MinioClient minioClient;

    public MinioKycDocumentStorageService(MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    @Override
    public String upload(Long userId, String kind, MultipartFile file) {
        try {
            String extension = EXTENSIONS_BY_CONTENT_TYPE.getOrDefault(file.getContentType(), "");

            String documentKey = "kyc/" + userId + "/" + kind + "/" + UUID.randomUUID() + extension;

            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(BUCKET_NAME)
                            .object(documentKey)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );

            log.info("Uploaded KYC {} for userId {} with key {}", kind, userId, documentKey);

            return documentKey;

        } catch (Exception ex) {
            log.error("Failed to upload KYC {} for userId {}", kind, userId, ex);
            throw new KycDocumentStorageException("Failed to upload document to storage");
        }
    }

    @Override
    public void delete(String documentKey) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder().bucket(BUCKET_NAME).object(documentKey).build());
            log.info("Deleted KYC document {}", documentKey);
        } catch (Exception ex) {
            log.error("Failed to delete KYC document {}", documentKey, ex);
            throw new KycDocumentStorageException("Failed to delete document from storage");
        }
    }

    @Override
    public String getDocumentUrl(String documentKey) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(BUCKET_NAME)
                            .object(documentKey)
                            .expiry(60 * 60)
                            .build()
            );
        } catch (Exception ex) {
            log.error("Failed to generate URL for KYC document {}", documentKey, ex);
            throw new KycDocumentStorageException("Failed to generate document URL");
        }
    }
}
