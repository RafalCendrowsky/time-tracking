package com.timetracking.project.web.dto;

import com.timetracking.project.model.domain.ProjectRole;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AssignRoleRequest(
        @NotNull UUID userId,
        @NotNull ProjectRole role
) {
}

