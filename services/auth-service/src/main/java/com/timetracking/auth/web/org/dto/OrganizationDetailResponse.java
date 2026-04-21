package com.timetracking.auth.web.org.dto;

import com.timetracking.auth.model.domain.Organization;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public record OrganizationDetailResponse(
        UUID id,
        String name,
        Set<String> domains,
        IdpConfigDto externalIdp
) {
    public record IdpConfigDto(String clientId, String clientSecretRef, String discoveryUri) {
    }

    public static OrganizationDetailResponse from(Organization org) {
        var idpDto = Optional.ofNullable(org.getExternalIdp())
                .map(idp -> new IdpConfigDto(idp.getClientId(), idp.getClientSecretRef(), idp.getDiscoveryUri()))
                .orElse(null);
        return new OrganizationDetailResponse(org.getId(), org.getName(), org.getDomains(), idpDto);
    }
}

