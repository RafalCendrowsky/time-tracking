package com.timetracking.auth.service;

import com.timetracking.auth.config.properties.VaultProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.Versioned;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class VaultService {
    private static final String VALUE_FIELD = "value";

    private final VaultTemplate vaultTemplate;
    private final VaultProperties vaultProperties;
    private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

    public String get(String key) {
        return cache.computeIfAbsent(
                key, k -> readFromVault(k).orElseThrow(() -> new IllegalStateException(
                        "Required secret not found in Vault at path: " + fullPath(k)
                ))
        );
    }

    private Optional<String> readFromVault(String key) {
        var response = vaultTemplate.opsForVersionedKeyValue(vaultProperties.kvMount())
                .get(fullPath(key), Map.class);

        return Optional.ofNullable(response)
                .map(Versioned::getData)
                .map(data -> data.get(VALUE_FIELD))
                .map(Object::toString);
    }

    private String fullPath(String key) {
        return vaultProperties.secretPath() + "/" + key;
    }
}
