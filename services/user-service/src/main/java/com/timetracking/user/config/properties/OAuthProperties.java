package com.timetracking.user.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "oauth2.internal")
public record OAuthProperties(
        String issuer,
        Client client,
        Token token
) {

    public record Client(String id, String redirectUri, String postLogoutRedirectUri) {
    }

    public record Token(Duration accessTokenTtl) {
    }
}

