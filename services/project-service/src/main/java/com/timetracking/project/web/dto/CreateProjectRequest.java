package com.timetracking.project.web.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateProjectRequest(
        @NotNull String name,
        @NotNull UUID parentId
) {
}

