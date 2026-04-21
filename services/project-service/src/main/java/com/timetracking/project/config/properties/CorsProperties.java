package com.timetracking.project.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "cors")
public record CorsProperties(
        List<String> allowedOrigins,
        List<String> allowedMethods,
        List<String> allowedHeaders,
        List<String> exposedHeaders,
        boolean allowCredentials,
        long maxAgeSeconds
) {
    public CorsProperties {
        if (allowedOrigins == null) allowedOrigins = List.of();
        if (allowedMethods == null) allowedMethods = List.of("GET", "POST", "PUT", "DELETE", "OPTIONS");
        if (allowedHeaders == null) allowedHeaders = List.of("Authorization", "Content-Type", "X-Requested-With");
        if (exposedHeaders == null) exposedHeaders = List.of();
        if (maxAgeSeconds == 0) maxAgeSeconds = 1800L;
    }
}

