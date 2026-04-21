package com.timetracking.project.mapper;

import com.timetracking.project.model.domain.Project;
import com.timetracking.project.web.dto.ProjectTreeResponse;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public final class ProjectTreeMapper {
    private static final Comparator<ProjectTreeResponse> TREE_ORDER = Comparator
            .comparing(ProjectTreeResponse::name, Comparator.nullsLast(String::compareToIgnoreCase))
            .thenComparing(ProjectTreeResponse::createdAt, Comparator.nullsLast(Comparator.naturalOrder()));

    private ProjectTreeMapper() {
    }

    public static List<ProjectTreeResponse> toForest(List<Project> projects) {
        var nodes = projects.stream()
                .collect(Collectors.toMap(Project::getId, ProjectTreeResponse::from));
        var roots = new ArrayList<ProjectTreeResponse>();


        projects.forEach(project -> Optional.ofNullable(nodes.get(project.getParentId()))
                .ifPresentOrElse(
                        parent -> parent.children().add(nodes.get(project.getId())),
                        () -> roots.add(nodes.get(project.getId()))
                ));

        nodes.forEach((_, response) -> response.children().sort(TREE_ORDER));
        return roots.stream()
                .sorted(TREE_ORDER)
                .toList();
    }
}

