package com.timetracking.project.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth-service")
public record AuthServiceProperties(String baseUrl) {
}

