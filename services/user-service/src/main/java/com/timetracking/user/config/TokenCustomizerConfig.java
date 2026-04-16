package com.timetracking.user.config;

import com.timetracking.user.dto.ExternalUserPrincipal;
import com.timetracking.user.dto.InternalUserPrincipal;
import com.timetracking.user.model.domain.UserAccount;
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
                    context.getClaims().subject(user.getId());
                    if (isAccessToken) {
                        context.getClaims().claim("roles", user.getRoles());
                    }
                }
                case ExternalUserPrincipal(UserAccount user, _) -> {
                    context.getClaims().subject(user.getId());
                    if (isAccessToken) {
                        context.getClaims().claim("organization_id", user.getOrganizationId());
                        context.getClaims().claim("roles", user.getRoles());
                    }
                }
                case null, default -> {
                }
            }
        };
    }
}
