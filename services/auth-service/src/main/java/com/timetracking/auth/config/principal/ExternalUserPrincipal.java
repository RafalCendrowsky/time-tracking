package com.timetracking.auth.config.principal;

import com.timetracking.auth.constant.UserRole;
import com.timetracking.auth.model.domain.UserAccount;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record ExternalUserPrincipal(UserAccount user, OidcUser delegate) implements OidcUser, UserPrincipal {
    @Override
    public Map<String, Object> getClaims() {
        return delegate.getClaims();
    }

    @Override
    public OidcUserInfo getUserInfo() {
        return delegate.getUserInfo();
    }

    @Override
    public OidcIdToken getIdToken() {
        return delegate.getIdToken();
    }

    @Override
    public Map<String, Object> getAttributes() {
        return delegate.getAttributes();
    }

    @Override
    public @NonNull Collection<? extends GrantedAuthority> getAuthorities() {
        return user.getRoles().stream()
                .map(role -> (GrantedAuthority) () -> "ROLE_" + role)
                .toList();
    }

    @Override
    public @NonNull String getName() {
        return user.getId().toString();
    }

    @Override
    public UUID userId() {
        return user.getId();
    }

    @Override
    public UUID organizationId() {
        return user.getOrganizationId();
    }

    @Override
    public Set<UserRole> roles() {
        return user.getRoles();
    }
}
