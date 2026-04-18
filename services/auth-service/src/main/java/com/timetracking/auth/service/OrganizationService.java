package com.timetracking.auth.service;

import com.timetracking.auth.model.domain.Organization;
import com.timetracking.auth.model.repository.OrganizationRepository;
import com.timetracking.auth.web.org.dto.OrganizationRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class OrganizationService {
    private final OrganizationRepository organizationRepository;
    private final ClientRegistrationService clientRegistrationService;

    public List<Organization> findAll() {
        return organizationRepository.findAll();
    }

    public Organization findById(String id) {
        return organizationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found: " + id));
    }

    public Organization create(OrganizationRequest request) {
        return organizationRepository.save(applyRequest(new Organization(), request));
    }

    public Organization update(String id, OrganizationRequest request) {
        var org = applyRequest(findById(id), request);
        clientRegistrationService.evict(id);
        return organizationRepository.save(org);
    }

    public void delete(String id) {
        if (!organizationRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found: " + id);
        }
        clientRegistrationService.evict(id);
        organizationRepository.deleteById(id);
    }

    private Organization applyRequest(Organization org, OrganizationRequest request) {
        org.setName(request.name());
        org.setDomains(Optional.ofNullable(request.domains()).orElseGet(LinkedHashSet::new));
        org.setExternalIdp(toIdpConfig(request.externalIdp()));
        return org;
    }

    private Organization.IdpConfig toIdpConfig(OrganizationRequest.IdpConfigRequest request) {
        if (request == null) {
            return null;
        }
        return new Organization.IdpConfig(request.clientId(), request.clientSecretRef(), request.discoveryUri());
    }
}
