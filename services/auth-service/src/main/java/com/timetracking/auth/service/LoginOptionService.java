package com.timetracking.auth.service;

import com.timetracking.auth.constant.LoginOptionType;
import com.timetracking.auth.dto.LoginOption;
import com.timetracking.auth.model.domain.Organization;
import com.timetracking.auth.model.repository.OrganizationRepository;
import com.timetracking.auth.util.NormalizationUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.EnumMap;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LoginOptionService {

    private final UserService userService;
    private final OrganizationRepository organizationRepository;

    public List<LoginOption> findLoginOptions(String email) {
        var normalizedEmail = NormalizationUtil.normalizeEmail(email);
        var options = new EnumMap<LoginOptionType, LoginOption>(LoginOptionType.class);

        userService.findByEmail(normalizedEmail).ifPresent(account -> {
            if (StringUtils.hasText(account.getPasswordHash())) {
                options.put(LoginOptionType.INTERNAL, LoginOption.internal("/api/auth/login/internal"));
            }

            if (account.getOrganizationId() != null) {
                organizationRepository.findById(account.getOrganizationId())
                        .filter(org -> org.getExternalIdp() != null)
                        .ifPresent(org -> options.put(
                                LoginOptionType.EXTERNAL,
                                externalOption(org)
                        ));
            }
        });

        if (!options.containsKey(LoginOptionType.EXTERNAL)) {
            var domain = extractDomain(normalizedEmail);
            for (Organization org : organizationRepository.findByDomainsContaining(domain)) {
                if (org.getExternalIdp() != null) {
                    options.put(LoginOptionType.EXTERNAL, externalOption(org));
                    break;
                }
            }
        }

        return options.values().stream().toList();
    }

    private static LoginOption externalOption(Organization org) {
        return LoginOption.external(org.getId().toString(), org.getName(), "/api/auth/login/external/" + org.getId());
    }

    private static String extractDomain(String email) {
        int atIndex = email.indexOf('@');
        if (atIndex < 0 || atIndex == email.length() - 1) {
            throw new IllegalArgumentException("Email must contain a domain");
        }
        return email.substring(atIndex + 1);
    }
}

