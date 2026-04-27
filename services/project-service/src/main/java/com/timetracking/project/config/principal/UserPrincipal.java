package com.timetracking.project.config.principal;

import java.util.List;
import java.util.UUID;

public record UserPrincipal(
        UUID id,
        UUID organizationId,
        List<String> roles
) {
    public boolean isOrganizationAdmin() {
        return organizationId != null && roles.contains("ORGANIZATION_ADMIN");
    }
}

