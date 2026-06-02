package com.rajat.rent_anything.item.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "storage")
public class StorageProperties {
    private String bucketName;

    private String endpoint;

    private String accessKey;

    private String secretKey;
}
