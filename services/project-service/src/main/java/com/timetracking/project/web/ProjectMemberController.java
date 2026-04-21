package com.timetracking.project.web;

import com.timetracking.project.security.UserPrincipal;
import com.timetracking.project.service.ProjectMemberService;
import com.timetracking.project.web.dto.AssignRoleRequest;
import com.timetracking.project.web.dto.ProjectMemberResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/members")
@RequiredArgsConstructor
public class ProjectMemberController {

    private final ProjectMemberService projectMemberService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectMemberResponse assignRole(
            @PathVariable UUID projectId,
            @Valid @RequestBody AssignRoleRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return projectMemberService.assignRole(projectId, request, principal);
    }

    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeRole(
            @PathVariable UUID projectId,
            @PathVariable UUID userId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        projectMemberService.removeRole(projectId, userId, principal);
    }

    @GetMapping
    public List<ProjectMemberResponse> listMembers(@PathVariable UUID projectId) {
        return projectMemberService.listMembers(projectId);
    }
}
