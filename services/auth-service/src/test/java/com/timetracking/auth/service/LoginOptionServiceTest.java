package com.timetracking.auth.service;

import com.timetracking.auth.constant.LoginOptionType;
import com.timetracking.auth.dto.LoginOption;
import com.timetracking.auth.model.domain.Organization;
import com.timetracking.auth.model.domain.UserAccount;
import com.timetracking.auth.model.repository.OrganizationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

public class LoginOptionServiceTest {
    @MockitoBean
    private UserService userService;

    @MockitoBean
    private OrganizationRepository organizationRepository;

    @Test
    void resolveLoginOptionsReturnsPersonalAndOrganizationLoginWhenAccountHasBoth() {
        var loginOptionService = new LoginOptionService(userService, organizationRepository);
        UserAccount account = new UserAccount(
                "user-1",
                "alice@example.com",
                "hashed",
                "org-1",
                Set.of("USER"),
                Instant.now(),
                Instant.now()
        );
        Organization organization = new Organization(
                "org-1",
                "Acme",
                Set.of("example.com"),
                new Organization.IdpConfig("client-id", "env:ORG_CLIENT_SECRET", "https://idp.example.com")
        );
        when(userService.findByEmail("alice@example.com")).thenReturn(Optional.of(account));

        List<LoginOption> options = loginOptionService.findLoginOptions("alice@example.com");

        assertThat(options).extracting(LoginOption::type)
                .containsExactly(LoginOptionType.INTERNAL, LoginOptionType.EXTERNAL);
        assertThat(options).extracting(LoginOption::loginUrl)
                .containsExactly("/api/auth/login/internal", "/api/auth/login/external/org-1");
    }

    @Test
    void resolveLoginOptionsFallsBackToOrganizationDomainWhenNoPersonalAccountExists() {
        var loginOptionService = new LoginOptionService(userService, organizationRepository);
        Organization organization = new Organization(
                "org-1",
                "Acme",
                Set.of("example.com"),
                new Organization.IdpConfig("client-id", "ORG_CLIENT_SECRET", "https://idp.example.com")
        );
        when(userService.findByEmail("alice@example.com")).thenReturn(Optional.empty());
        when(organizationRepository.findByDomainsContaining("example.com")).thenReturn(List.of(organization));

        List<LoginOption> options = loginOptionService.findLoginOptions("alice@example.com");

        assertThat(options).hasSize(1);
        assertThat(options.getFirst().type()).isEqualTo("EXTERNAL");
        assertThat(options.getFirst().organizationId()).isEqualTo("org-1");
    }
}
