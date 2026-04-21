package com.timetracking.auth.config;

import com.timetracking.auth.dto.ExternalUserPrincipal;
import com.timetracking.auth.dto.InternalUserPrincipal;
import com.timetracking.auth.model.domain.UserAccount;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;

@Configuration
public class TokenCustomizerConfig {

    @Bean
    public OAuth2TokenCustomizer<JwtEncodingContext> jwtCustomizer() {
        return context -> {
            var isAccessToken = context.getTokenType() == OAuth2TokenType.ACCESS_TOKEN;
            switch (context.getPrincipal().getPrincipal()) {
                case InternalUserPrincipal(UserAccount user) -> {
                    context.getClaims().subject(user.getId().toString());
                    if (isAccessToken) {
                        context.getClaims().claim("roles", user.getRoles());
                    }
                }
                case ExternalUserPrincipal(UserAccount user, _) -> {
                    context.getClaims().subject(user.getId().toString());
                    if (isAccessToken) {
                        if (user.getOrganizationId() != null) {
                            context.getClaims().claim("organization_id", user.getOrganizationId().toString());
                        }
                        context.getClaims().claim("roles", user.getRoles());
                    }
                }
                case null, default -> {
                }
            }
        };
    }
}
