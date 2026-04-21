package com.timetracking.project.service;

import com.timetracking.project.dto.CreateProjectRequest;
import com.timetracking.project.dto.ProjectResponse;
import com.timetracking.project.dto.UpdateProjectRequest;
import com.timetracking.project.exception.ProjectNotFoundException;
import com.timetracking.project.model.domain.Project;
import com.timetracking.project.model.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;

    @Transactional
    public ProjectResponse create(CreateProjectRequest request, UUID userId) {
        var parent = projectRepository.findById(request.parentId())
                .orElseThrow(() -> new ProjectNotFoundException("Parent project not found: " + request.parentId()));

        var project = Project.builder()
                .name(request.name())
                .parentId(parent.getId())
                .organizationId(parent.getOrganizationId())
                .ownerId(userId)
                .build();

        return toResponse(projectRepository.save(project));
    }

    @Transactional(readOnly = true)
    public ProjectResponse getById(UUID id) {
        return toResponse(findById(id));
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> getAll() {
        return projectRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ProjectResponse update(UUID id, UpdateProjectRequest request) {
        var project = findById(id);

        if (request.name() != null) {
            project.setName(request.name());
        }
        return toResponse(projectRepository.save(project));
    }

    @Transactional
    public void delete(UUID id) {
        if (!projectRepository.existsById(id)) {
            throw new ProjectNotFoundException("Project not found: " + id);
        }
        projectRepository.deleteById(id);
    }

    private Project findById(UUID id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new ProjectNotFoundException("Project not found: " + id));
    }

    private ProjectResponse toResponse(Project project) {
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

