package com.timetracking.project.web.dto;

import com.timetracking.project.model.domain.Project;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ProjectResponse(
        UUID id,
        String name,
        UUID parentId,
        UUID organizationId,
        UUID ownerId,
        OffsetDateTime createdAt
) {
    public static ProjectResponse from(Project project) {
        return new ProjectResponse(
                project.getId(),
                project.getName(),
                project.getParentId(),
                project.getOrganizationId(),
                project.getOwnerId(),
                project.getCreatedAt()
        );
    }
}

