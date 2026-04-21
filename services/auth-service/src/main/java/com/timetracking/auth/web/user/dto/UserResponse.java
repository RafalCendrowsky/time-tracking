package com.timetracking.auth.web.user.dto;

import com.timetracking.auth.constant.UserRole;
import com.timetracking.auth.model.domain.UserAccount;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String firstName,
        String lastName,
        UUID organizationId,
        Set<UserRole> roles,
        Instant createdAt,
        Instant updatedAt
) {
    public static UserResponse from(UserAccount account) {
        return new UserResponse(
                account.getId(),
                account.getEmail(),
                account.getFirstName(),
                account.getLastName(),
                account.getOrganizationId(),
                account.getRoles(),
                account.getCreatedAt(),
                account.getUpdatedAt()
        );
    }
}

