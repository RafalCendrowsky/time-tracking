package com.timetracking.auth.config.principal;

import com.timetracking.auth.constant.UserRole;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class UserPrincipalJwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        var userId = UUID.fromString(jwt.getSubject());
        var organizationId = Optional.ofNullable(jwt.getClaimAsString("organization_id"))
                .filter(value -> !value.isBlank())
                .map(UUID::fromString)
                .orElse(null);

        var roles = Optional.ofNullable(jwt.getClaimAsStringList("roles"))
                .orElse(List.of())
                .stream()
                .map(UserRole::valueOf)
                .collect(Collectors.toUnmodifiableSet());

        var authorities = roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                .toList();

        return new UserPrincipalAuthenticationToken(
                new JwtUserPrincipal(userId, organizationId, roles),
                authorities
        );
    }
}

