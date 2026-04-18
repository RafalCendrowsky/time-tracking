package com.timetracking.auth.web.org.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.LinkedHashSet;

public record OrganizationRequest(
        @NotBlank String name,
        LinkedHashSet<String> domains,
        IdpConfigRequest externalIdp
) {
    public record IdpConfigRequest(
            @NotBlank String clientId,
            @NotBlank String clientSecretRef,
            @NotBlank String discoveryUri
    ) {
    }
}

