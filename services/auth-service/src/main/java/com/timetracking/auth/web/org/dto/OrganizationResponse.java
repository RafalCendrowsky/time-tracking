package com.timetracking.auth.web.org.dto;

import com.timetracking.auth.model.domain.Organization;

import java.util.Set;
import java.util.UUID;

public record OrganizationResponse(
        UUID id,
        String name,
        Set<String> domains,
        boolean hasExternalIdp
) {
    public static OrganizationResponse from(Organization org) {
        return new OrganizationResponse(
                org.getId(),
                org.getName(),
                org.getDomains(),
                org.getExternalIdp() != null
        );
    }
}

