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
import java.util.UUID;

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
        var account = new UserAccount(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "alice@example.com",
                "hashed",
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                Set.of(com.timetracking.auth.constant.UserRole.USER),
                "Alice",
                "Example",
                Instant.now(),
                Instant.now()
        );
        when(userService.findByEmail("alice@example.com")).thenReturn(Optional.of(account));

        List<LoginOption> options = loginOptionService.findLoginOptions("alice@example.com");

        assertThat(options).extracting(LoginOption::type)
                .containsExactly(LoginOptionType.INTERNAL, LoginOptionType.EXTERNAL);
        assertThat(options).extracting(LoginOption::loginUrl)
                .containsExactly(
                        "/api/auth/login/internal",
                        "/api/auth/login/external/22222222-2222-2222-2222-222222222222"
                );
    }

    @Test
    void resolveLoginOptionsFallsBackToOrganizationDomainWhenNoPersonalAccountExists() {
        var loginOptionService = new LoginOptionService(userService, organizationRepository);
        Organization organization = new Organization(
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                "Acme",
                Set.of("example.com"),
                new Organization.IdpConfig("client-id", "ORG_CLIENT_SECRET", "https://idp.example.com")
        );
        when(userService.findByEmail("alice@example.com")).thenReturn(Optional.empty());
        when(organizationRepository.findByDomainsContaining("example.com")).thenReturn(List.of(organization));

        List<LoginOption> options = loginOptionService.findLoginOptions("alice@example.com");

        assertThat(options).hasSize(1);
        assertThat(options.getFirst().type()).isEqualTo("EXTERNAL");
        assertThat(options.getFirst().organizationId()).isEqualTo("22222222-2222-2222-2222-222222222222");
    }
}
