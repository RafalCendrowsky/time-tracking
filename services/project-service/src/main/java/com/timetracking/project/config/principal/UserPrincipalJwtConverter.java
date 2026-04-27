package com.timetracking.project.config.principal;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class UserPrincipalJwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        var userId = UUID.fromString(jwt.getSubject());
        var organizationId = Optional.ofNullable(jwt.getClaimAsString("organization_id"))
                .map(UUID::fromString)
                .orElse(null);

        var roles = Optional.ofNullable(jwt.getClaimAsStringList("roles")).orElse(List.of());
        var authorities = roles.stream()
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                .toList();

        return new UserPrincipalAuthenticationToken(
                new UserPrincipal(userId, organizationId, roles),
                jwt.getTokenValue(),
                authorities
        );
    }
}

