package com.timetracking.project.service;

import com.timetracking.project.exception.NotFoundException;
import com.timetracking.project.exception.ValidationException;
import com.timetracking.project.mapper.ProjectTreeMapper;
import com.timetracking.project.model.domain.Project;
import com.timetracking.project.model.repository.ProjectRepository;
import com.timetracking.project.security.UserPrincipal;
import com.timetracking.project.web.dto.CreateProjectRequest;
import com.timetracking.project.web.dto.ProjectResponse;
import com.timetracking.project.web.dto.ProjectTreeResponse;
import com.timetracking.project.web.dto.UpdateProjectRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;

    @Transactional(readOnly = true)
    public List<ProjectTreeResponse> findAllFor(UserPrincipal principal) {
        List<Project> projects;
        if (principal.isOrganizationAdmin()) {
            projects = projectRepository.findAllByOrganizationId(principal.organizationId());
        } else if (principal.organizationId() != null) {
            projects = projectRepository.findAccessibleProjects(principal.id(), principal.organizationId());
        } else {
            projects = projectRepository.findAllByOwnerId(principal.id());
        }
        return ProjectTreeMapper.toForest(projects);
    }

    @Transactional(readOnly = true)
    public ProjectResponse getById(UUID id) {
        return ProjectResponse.from(findById(id));
    }

    @Transactional
    public ProjectResponse create(CreateProjectRequest request, UUID userId) {
        var parent = projectRepository.findById(request.parentId())
                .orElseThrow(() -> new NotFoundException("Parent project not found: " + request.parentId()));
        
        if (parent.getOrganizationId() == null && parent.getParentId() != null) {
            throw new ValidationException("Personal projects can only be 1 level deep");
        }

        var project = Project.builder()
                .name(request.name())
                .parentId(parent.getId())
                .organizationId(parent.getOrganizationId())
                .ownerId(userId)
                .build();

        return ProjectResponse.from(projectRepository.save(project));
    }

    @Transactional
    public ProjectResponse update(UUID id, UpdateProjectRequest request) {
        var project = findById(id);

        if (request.name() != null) {
            project.setName(request.name());
        }
        return ProjectResponse.from(projectRepository.save(project));
    }

    @Transactional
    public void delete(UUID id) {
        if (!projectRepository.existsById(id)) {
            throw new NotFoundException("Project not found: " + id);
        }
        projectRepository.deleteById(id);
    }

    private Project findById(UUID id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Project not found: " + id));
    }
}
