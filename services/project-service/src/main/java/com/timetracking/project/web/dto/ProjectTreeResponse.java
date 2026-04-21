package com.timetracking.project.web.dto;

import com.timetracking.project.model.domain.Project;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record ProjectTreeResponse(
        UUID id,
        String name,
        UUID parentId,
        UUID organizationId,
        UUID ownerId,
        OffsetDateTime createdAt,
        List<ProjectTreeResponse> children
) {
    public static ProjectTreeResponse from(Project project) {
        return new ProjectTreeResponse(
                project.getId(),
                project.getName(),
                project.getParentId(),
                project.getOrganizationId(),
                project.getOwnerId(),
                project.getCreatedAt(),
                new ArrayList<>()
        );
    }
}

