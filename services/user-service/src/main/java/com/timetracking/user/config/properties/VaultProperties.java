package com.timetracking.user.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;

@ConfigurationProperties(prefix = "vault")
public record VaultProperties(
        URI uri,
        String token,
        String kvMount,
        String secretPath
) {
}

