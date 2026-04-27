package com.timetracking.project.config.principal;

import org.jspecify.annotations.NonNull;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

public class UserPrincipalAuthenticationToken extends AbstractAuthenticationToken {

    private final UserPrincipal principal;
    private final String bearerToken;

    public UserPrincipalAuthenticationToken(
            @NonNull UserPrincipal principal,
            @NonNull String bearerToken,
            Collection<? extends GrantedAuthority> authorities
    ) {
        super(authorities);
        this.principal = principal;
        this.bearerToken = bearerToken;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return bearerToken;
    }

    @Override
    public @NonNull UserPrincipal getPrincipal() {
        return principal;
    }
}

