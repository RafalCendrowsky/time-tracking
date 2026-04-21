package com.timetracking.project.mapper;

import com.timetracking.project.model.domain.Project;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectTreeMapperTest {

    @Test
    void toForestBuildsNestedTreeAndPromotesOrphansToRoots() {
        var rootId = UUID.randomUUID();
        var childId = UUID.randomUUID();
        var grandChildId = UUID.randomUUID();
        var orphanId = UUID.randomUUID();
        var missingParentId = UUID.randomUUID();

        var projects = List.of(
                Project.builder().id(childId).name("Child").parentId(rootId).build(),
                Project.builder().id(orphanId).name("Orphan").parentId(missingParentId).build(),
                Project.builder().id(rootId).name("Root").build(),
                Project.builder().id(grandChildId).name("Grandchild").parentId(childId).build()
        );

        var forest = ProjectTreeMapper.toForest(projects);

        assertEquals(2, forest.size());
        assertTrue(forest.stream().anyMatch(node -> node.id().equals(rootId)));
        assertTrue(forest.stream().anyMatch(node -> node.id().equals(orphanId)));

        var root = forest.stream()
                .filter(node -> node.id().equals(rootId))
                .findFirst()
                .orElseThrow();
        assertEquals(1, root.children().size());
        assertEquals(childId, root.children().getFirst().id());
        assertEquals(1, root.children().getFirst().children().size());
        assertEquals(grandChildId, root.children().getFirst().children().getFirst().id());
    }
}

