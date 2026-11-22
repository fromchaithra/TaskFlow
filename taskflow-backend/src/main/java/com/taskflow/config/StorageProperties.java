package com.taskflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "taskflow.storage")
public record StorageProperties(
        String provider,
        String localDir,
        long maxFileSizeBytes
) {
}
