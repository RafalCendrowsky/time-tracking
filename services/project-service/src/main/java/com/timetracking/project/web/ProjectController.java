package com.timetracking.project.web;

import com.timetracking.project.config.principal.UserPrincipal;
import com.timetracking.project.service.ProjectService;
import com.timetracking.project.web.dto.CreateProjectRequest;
import com.timetracking.project.web.dto.ProjectResponse;
import com.timetracking.project.web.dto.ProjectTreeResponse;
import com.timetracking.project.web.dto.UpdateProjectRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @GetMapping
    public List<ProjectTreeResponse> getAll(@AuthenticationPrincipal UserPrincipal principal) {
        return projectService.findAllFor(principal);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasPermission(#request.parentId(), 'MANAGE_SUBPROJECT')")
    public ProjectResponse create(
            @Valid @RequestBody CreateProjectRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return projectService.create(request, principal.id());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission(#id, 'VIEW_PROJECT')")
    public ProjectResponse getById(@PathVariable UUID id) {
        return projectService.getById(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission(#id, 'MANAGE_PROJECT')")
    public ProjectResponse update(
            @PathVariable UUID id,
            @RequestBody UpdateProjectRequest request
    ) {
        return projectService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasPermission(#id, 'MANAGE_SUBPROJECT')")
    public void delete(@PathVariable UUID id) {
        projectService.delete(id);
    }
}
