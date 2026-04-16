package com.timetracking.user.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "oauth2")
public record OAuthProperties(
        String issuer,
        Client client,
        Token token
) {

    public record Client(String id, String secret, String redirectUri, String postLogoutRedirectUri) {
    }

    public record Token(Duration accessTokenTtl) {
    }
}

