package com.timetracking.auth.config.principal;

import com.timetracking.auth.constant.UserRole;

import java.util.Set;
import java.util.UUID;

public interface UserPrincipal {
    UUID userId();

    UUID organizationId();

    Set<UserRole> roles();

    default boolean isAdmin() {
        return roles().contains(UserRole.ADMIN);
    }
}

