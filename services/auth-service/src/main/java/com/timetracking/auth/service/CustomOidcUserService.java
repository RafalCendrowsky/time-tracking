package com.timetracking.auth.service;

import com.timetracking.auth.config.principal.ExternalUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {
    private final UserService authService;

    @Override
    public ExternalUserPrincipal loadUser(OidcUserRequest userRequest) {
        var oidcUser = new OidcUserService().loadUser(userRequest);

        var email = oidcUser.getEmail();
        var organizationId = UUID.fromString(userRequest.getClientRegistration().getRegistrationId());
        var firstName = oidcUser.getGivenName();
        var lastName = oidcUser.getFamilyName();
        var user = authService.resolveOrProvisionExternalAccount(email, organizationId, firstName, lastName);

        return new ExternalUserPrincipal(user, oidcUser);
    }
}
