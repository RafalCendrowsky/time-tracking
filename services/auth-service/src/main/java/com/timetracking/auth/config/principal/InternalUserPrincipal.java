package com.timetracking.auth.config.principal;

import com.timetracking.auth.constant.UserRole;
import com.timetracking.auth.model.domain.UserAccount;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

public record InternalUserPrincipal(UserAccount user) implements UserDetails, UserPrincipal {

    @Override
    @NonNull
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return user.getRoles().stream()
                .map(role -> (GrantedAuthority) () -> "ROLE_" + role.name())
                .toList();
    }

    @Override
    public String getPassword() {
        return user.getPasswordHash();
    }

    @Override
    @NonNull
    public String getUsername() {
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
