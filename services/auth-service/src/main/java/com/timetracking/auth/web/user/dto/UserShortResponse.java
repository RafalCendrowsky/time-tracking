package com.timetracking.auth.web.user.dto;

import com.timetracking.auth.model.domain.UserAccount;

import java.util.UUID;

public record UserShortResponse(
        UUID id,
        String email,
        String firstName,
        String lastName,
        UUID organizationId
) {
    public static UserShortResponse from(UserAccount account) {
        return new UserShortResponse(
                account.getId(),
                account.getEmail(),
                account.getFirstName(),
                account.getLastName(),
                account.getOrganizationId()
        );
    }
}
