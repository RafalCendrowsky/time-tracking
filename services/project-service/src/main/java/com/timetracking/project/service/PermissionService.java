package com.timetracking.project.service;

import com.timetracking.project.model.domain.ProjectRole;
import com.timetracking.project.model.repository.ProjectMemberRepository;
import com.timetracking.project.model.repository.ProjectRepository;
import com.timetracking.project.security.UserPrincipal;
import com.timetracking.project.security.UserPrincipalAuthenticationToken;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PermissionService implements PermissionEvaluator {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;

    @Override
    public boolean hasPermission(
            @NonNull Authentication authentication,
            @NonNull Object targetDomainObject,
            @NonNull Object permission
    ) {
        if (!(authentication instanceof UserPrincipalAuthenticationToken token)) {
            return false;
        }
        var principal = token.getPrincipal();
        var projectId = toUuid(targetDomainObject);
        if (projectId == null) {
            return false;
        }

        return resolveEffectiveRole(projectId, principal)
                .map(role -> evaluate(role, permission.toString()))
                .orElse(false);
    }

    @Override
    public boolean hasPermission(
            @NonNull Authentication authentication,
            @NonNull Serializable targetId,
            @NonNull String targetType,
            @NonNull Object permission
    ) {
        return hasPermission(authentication, targetId, permission);
    }

    Optional<ProjectRole> resolveEffectiveRole(UUID projectId, UserPrincipal principal) {
        if (principal.isOrganizationAdmin()) {
            return projectRepository.findById(projectId)
                    .filter(p -> principal.organizationId().equals(p.getOrganizationId()))
                    .map(_ -> ProjectRole.ADMIN);
        }

        return projectMemberRepository.findEffectiveRoleInProject(projectId, principal.id())
                .map(ProjectRole::valueOf);
    }

    private boolean evaluate(ProjectRole role, String permission) {
        return switch (permission) {
            case "MANAGE_SUBPROJECT", "MANAGE_PROJECT" -> role.canManageSubprojects();
            case "ASSIGN_ADMIN_MANAGER" -> role.canManageAdminOrManager();
            case "ASSIGN_CONTRIBUTOR_VIEWER" -> role.canManageContributorOrViewer();
            case "VIEW_PROJECT" -> true;
            default -> false;
        };
    }

    private UUID toUuid(Object obj) {
        if (obj instanceof UUID u) {
            return u;
        }
        try {
            return UUID.fromString(obj.toString());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
