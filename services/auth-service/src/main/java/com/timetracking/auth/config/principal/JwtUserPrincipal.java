package com.timetracking.auth.config.principal;

import com.timetracking.auth.constant.UserRole;

import java.util.Set;
import java.util.UUID;

public record JwtUserPrincipal(
        UUID userId,
        UUID organizationId,
        Set<UserRole> roles
) implements UserPrincipal {
}

