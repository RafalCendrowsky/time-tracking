package com.timetracking.user.web;

import com.timetracking.user.model.domain.UserAccount;

import java.time.Instant;
import java.util.Set;

public record RegisterUserResponse(String id, String email, Set<String> roles, Instant createdAt, Instant updatedAt) {
    public static RegisterUserResponse from(UserAccount userAccount) {
        return new RegisterUserResponse(
                userAccount.getId(),
                userAccount.getEmail(),
                userAccount.getRoles(),
                userAccount.getCreatedAt(),
                userAccount.getUpdatedAt()
        );
    }
}
