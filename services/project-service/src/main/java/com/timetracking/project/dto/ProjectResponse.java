package com.timetracking.project.dto;

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
}

