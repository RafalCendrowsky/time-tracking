package com.timetracking.auth.web.user.dto;

import com.timetracking.auth.constant.UserRole;
import com.timetracking.auth.model.domain.UserAccount;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record RegisterUserResponse(
        UUID id,
        String email,
        String firstName,
        String lastName,
        Set<UserRole> roles,
        Instant createdAt,
        Instant updatedAt
) {
    public static RegisterUserResponse from(UserAccount userAccount) {
        return new RegisterUserResponse(
                userAccount.getId(),
                userAccount.getEmail(),
                userAccount.getFirstName(),
                userAccount.getLastName(),
                userAccount.getRoles(),
                userAccount.getCreatedAt(),
                userAccount.getUpdatedAt()
        );
    }
}

