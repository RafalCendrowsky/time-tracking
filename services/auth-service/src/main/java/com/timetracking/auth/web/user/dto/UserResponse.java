package com.timetracking.auth.web.user.dto;

import com.timetracking.auth.constant.UserRole;
import com.timetracking.auth.model.domain.UserAccount;

import java.time.Instant;
import java.util.Set;

public record UserResponse(
        String id,
        String email,
        String firstName,
        String lastName,
        String organizationId,
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

