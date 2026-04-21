package com.timetracking.project.web.dto;

import com.timetracking.project.model.domain.ProjectMember;
import com.timetracking.project.model.domain.ProjectRole;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ProjectMemberResponse(
        UUID id,
        UUID projectId,
        UUID userId,
        ProjectRole role,
        UUID grantedBy,
        OffsetDateTime grantedAt
) {
    public static ProjectMemberResponse from(ProjectMember member) {
        return new ProjectMemberResponse(
                member.getId(),
                member.getProjectId(),
                member.getUserId(),
                member.getRole(),
                member.getGrantedBy(),
                member.getGrantedAt()
        );
    }
}

