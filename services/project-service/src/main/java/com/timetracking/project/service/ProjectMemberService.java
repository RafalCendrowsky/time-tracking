package com.timetracking.project.service;

import com.timetracking.project.config.principal.UserPrincipal;
import com.timetracking.project.exception.NotFoundException;
import com.timetracking.project.exception.ValidationException;
import com.timetracking.project.model.domain.Project;
import com.timetracking.project.model.domain.ProjectMember;
import com.timetracking.project.model.domain.ProjectRole;
import com.timetracking.project.model.repository.ProjectMemberRepository;
import com.timetracking.project.model.repository.ProjectRepository;
import com.timetracking.project.web.dto.AssignRoleRequest;
import com.timetracking.project.web.dto.ProjectMemberResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProjectMemberService {
    private final static int MAX_PERSONAL_MEMBERS = 10;

    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectRepository projectRepository;
    private final PermissionService permissionEvaluator;
    private final UserProjectionService userProjectionService;

    @Transactional(readOnly = true)
    public List<ProjectMemberResponse> listMembers(UUID projectId) {
        verifyProjectExists(projectId);
        return projectMemberRepository.findByProjectId(projectId).stream()
                .map(ProjectMemberResponse::from)
                .toList();
    }

    @Transactional
    public ProjectMemberResponse assignRole(UUID projectId, AssignRoleRequest request, UserPrincipal caller) {
        var project = projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found: " + projectId));
        var callerRole = permissionEvaluator.resolveEffectiveRole(projectId, caller);
        var existingMember = projectMemberRepository.findByProjectIdAndUserUserId(projectId, request.userId());
        var userProjection = existingMember.map(ProjectMember::getUser)
                .orElseGet(() -> userProjectionService.getUser(request.userId()));

        if (userProjection.getOrganizationId() != project.getOrganizationId()) {
            throw new AccessDeniedException("You are not allowed to perform this action");
        }

        if (project.getOrganizationId() == null && existingMember.isEmpty()) {
            validatePersonalProjectSize(project);
        }

        var member = existingMember.orElseGet(() -> ProjectMember.builder()
                .projectId(projectId)
                .user(userProjection)
                .build());
        validateCallerCanAssign(callerRole, member, request.role());

        member.setRole(request.role());
        member.setGrantedBy(caller.id());

        return ProjectMemberResponse.from(projectMemberRepository.save(member));
    }

    @Transactional
    public void removeRole(UUID projectId, UUID targetUserId, UserPrincipal caller) {
        verifyProjectExists(projectId);
        var target = projectMemberRepository.findByProjectIdAndUserUserId(projectId, targetUserId)
                .orElseThrow(() -> new NotFoundException("Member not found for project %s and user %s".formatted(
                        projectId,
                        targetUserId
                )));

        var callerRole = permissionEvaluator.resolveEffectiveRole(projectId, caller);
        validateCallerCanAssign(callerRole, target, null);

        projectMemberRepository.deleteByProjectIdAndUserUserId(projectId, targetUserId);
    }

    private void validatePersonalProjectSize(Project project) {
        int currentCount = projectMemberRepository.countByProjectId(project.getId());
        if (currentCount >= MAX_PERSONAL_MEMBERS) {
            throw new ValidationException("Personal projects can contain at most %s users".formatted(
                    MAX_PERSONAL_MEMBERS));
        }
    }

    private void validateCallerCanAssign(
            Optional<ProjectRole> callerRole,
            ProjectMember member,
            ProjectRole assignRole
    ) {
        var targetRole = Optional.ofNullable(assignRole)
                .filter(r -> r.compareTo(member.getRole()) < 0)
                .orElse(member.getRole());
        var canAssign = callerRole.map(role -> switch (targetRole) {
            case ADMIN, MANAGER -> role.canManageAdminOrManager();
            case CONTRIBUTOR, VIEWER -> role.canManageContributorOrViewer();
        }).orElse(false);
        if (!canAssign) {
            throw new AccessDeniedException("Caller not allowed to assign role");
        }
    }

    private void verifyProjectExists(UUID projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new NotFoundException("Project not found: " + projectId);
        }
    }
}
