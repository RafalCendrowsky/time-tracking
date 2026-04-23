package com.timetracking.auth.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "spring.cloud.vault.kv")
public record VaultKvProperties(
        String backend,
        String defaultContext
) {
}

