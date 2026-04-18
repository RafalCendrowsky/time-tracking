package com.timetracking.auth.config;

import com.timetracking.auth.config.properties.VaultProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.vault.authentication.TokenAuthentication;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.core.VaultTemplate;

@Configuration
public class VaultConfig {
    @Bean
    VaultTemplate vaultTemplate(VaultProperties props) {
        var endpoint = VaultEndpoint.from(props.uri());
        var auth = new TokenAuthentication(props.token());
        return new VaultTemplate(endpoint, auth);
    }
}

