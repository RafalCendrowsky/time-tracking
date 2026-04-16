package com.timetracking.user.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;

import java.util.Objects;
import java.util.stream.Collectors;

@Configuration
public class TokenCustomizerConfig {

    @Bean
    public OAuth2TokenCustomizer<JwtEncodingContext> jwtCustomizer() {
        return context -> {
            Authentication principal = context.getPrincipal();
            if (principal != null && principal.getPrincipal() instanceof UserDetails user) {
                context.getClaims().claim("email", user.getUsername());
                context.getClaims().claim("preferred_username", user.getUsername());
                context.getClaims().claim(
                        "roles", user.getAuthorities().stream()
                                .map(GrantedAuthority::getAuthority)
                                .filter(Objects::nonNull)
                                .map(a -> a.startsWith("ROLE_") ? a.substring(5) : a)
                                .collect(Collectors.toList())
                );
            }
        };
    }
}

