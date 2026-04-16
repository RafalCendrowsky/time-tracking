package com.timetracking.user.service;

import com.timetracking.user.model.domain.Organization;
import com.timetracking.user.model.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.ClientRegistrations;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class ClientRegistrationService implements ClientRegistrationRepository {
    private final OrganizationRepository organizationRepository;
    private final VaultService vaultService;

    private final ConcurrentHashMap<String, ClientRegistration> clientRegistrationCache = new ConcurrentHashMap<>();


    @Override
    public ClientRegistration findByRegistrationId(String registrationId) {
        if (registrationId == null) {
            return null;
        }
        return clientRegistrationCache.computeIfAbsent(
                registrationId, id -> organizationRepository.findById(id)
                        .filter(it -> it.getExternalIdp() != null)
                        .map(this::createClientRegistration)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "No organization with external IdP found for id: " + id))
        );
    }

    private ClientRegistration createClientRegistration(Organization organization) {
        var idp = organization.getExternalIdp();
        var clientSecret = vaultService.get(idp.getClientSecretRef());
        return ClientRegistrations
                .fromIssuerLocation(idp.getDiscoveryUri())
                .registrationId(organization.getId())
                .clientId(idp.getClientId())
                .clientSecret(clientSecret)
                .clientName(organization.getName())
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/" + organization.getId())
                .scope(OidcScopes.OPENID, OidcScopes.PROFILE, OidcScopes.EMAIL)
                .build();
    }
}
