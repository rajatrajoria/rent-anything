package com.rajat.rent_anything.item.config;

import io.minio.MinioClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MinioConfiguration {

    @Bean
    public MinioClient minioClient() {

        return MinioClient.builder()
                .endpoint("http://localhost:9000")
                .credentials(
                        "minioadmin",
                        "minioadmin"
                )
                .build();
    }
}