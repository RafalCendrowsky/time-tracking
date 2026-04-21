package com.timetracking.project.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "oauth2.resource-server")
public record OAuth2ResourceServerProperties(String issuerUri) {
}

