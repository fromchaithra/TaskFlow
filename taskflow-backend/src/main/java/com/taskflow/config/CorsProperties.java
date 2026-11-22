package com.taskflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.List;

@ConfigurationProperties(prefix = "taskflow.cors")
public record CorsProperties(String allowedOrigins) {

    public List<String> allowedOriginList() {
        if (allowedOrigins == null || allowedOrigins.isBlank()) {
            return List.of("http://localhost:5173");
        }
        return Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
    }
}
