package com.rajat.rent_anything.item.application;

import com.rajat.rent_anything.item.exceptions.ImageStorageException;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MinioImageStorageServiceTest {

    @Mock
    private MinioClient minioClient;

    private MinioImageStorageService storageService;

    private static final Long ITEM_ID = 42L;

    @BeforeEach
    void setUp() {
        storageService = new MinioImageStorageService(minioClient);
    }

    @Test
    void upload_jpegFile_generatesKeyUnderItemPrefixWithJpgExtension() throws Exception {
        MultipartFile file = new MockMultipartFile("file", "whatever.exe", "image/jpeg", new byte[]{1, 2, 3});

        String key = storageService.upload(ITEM_ID, file);

        assertThat(key).startsWith("items/" + ITEM_ID + "/");
        assertThat(key).endsWith(".jpg");

        ArgumentCaptor<PutObjectArgs> captor = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(minioClient).putObject(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo("rent-anything");
        assertThat(captor.getValue().object()).isEqualTo(key);
    }

    @Test
    void upload_neverUsesClientSuppliedFilename() throws Exception {
        // A malicious filename must never leak into the generated object key.
        MultipartFile file = new MockMultipartFile("file", "../../../etc/passwd", "image/png", new byte[]{1});

        String key = storageService.upload(ITEM_ID, file);

        assertThat(key).doesNotContain("etc");
        assertThat(key).doesNotContain("passwd");
        assertThat(key).doesNotContain("..");
        assertThat(key).endsWith(".png");
    }

    @Test
    void upload_unrecognizedContentType_producesKeyWithNoExtension() throws Exception {
        MultipartFile file = new MockMultipartFile("file", "a.bin", "application/octet-stream", new byte[]{1});

        String key = storageService.upload(ITEM_ID, file);

        assertThat(key).startsWith("items/" + ITEM_ID + "/");
        assertThat(key).doesNotContain(".jpg", ".png", ".webp");
    }

    @Test
    void upload_minioClientThrows_wrapsInImageStorageException() throws Exception {
        MultipartFile file = new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[]{1});
        when(minioClient.putObject(any(PutObjectArgs.class))).thenThrow(new IOException("connection refused"));

        assertThrows(ImageStorageException.class, () -> storageService.upload(ITEM_ID, file));
    }

    @Test
    void delete_success_removesObjectFromConfiguredBucket() throws Exception {
        storageService.delete("items/42/abc.jpg");

        ArgumentCaptor<RemoveObjectArgs> captor = ArgumentCaptor.forClass(RemoveObjectArgs.class);
        verify(minioClient).removeObject(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo("rent-anything");
        assertThat(captor.getValue().object()).isEqualTo("items/42/abc.jpg");
    }

    @Test
    void delete_minioClientThrows_wrapsInImageStorageException() throws Exception {
        doThrow(new IOException("boom")).when(minioClient).removeObject(any(RemoveObjectArgs.class));

        assertThrows(ImageStorageException.class, () -> storageService.delete("items/42/abc.jpg"));
    }

    @Test
    void getImageUrl_success_returnsPresignedUrlForConfiguredBucket() throws Exception {
        when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .thenReturn("https://minio.local/rent-anything/items/42/abc.jpg?sig=xyz");

        String url = storageService.getImageUrl("items/42/abc.jpg");

        assertThat(url).isEqualTo("https://minio.local/rent-anything/items/42/abc.jpg?sig=xyz");

        ArgumentCaptor<GetPresignedObjectUrlArgs> captor = ArgumentCaptor.forClass(GetPresignedObjectUrlArgs.class);
        verify(minioClient).getPresignedObjectUrl(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo("rent-anything");
        assertThat(captor.getValue().object()).isEqualTo("items/42/abc.jpg");
    }

    @Test
    void getImageUrl_minioClientThrows_wrapsInImageStorageException() throws Exception {
        when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .thenThrow(new IOException("boom"));

        assertThrows(ImageStorageException.class, () -> storageService.getImageUrl("items/42/abc.jpg"));
    }
}
