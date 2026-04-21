package com.timetracking.project.model.repository;

import com.timetracking.project.model.domain.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ProjectRepository extends JpaRepository<Project, UUID> {
    List<Project> findAllByOwnerId(UUID id);

    List<Project> findAllByOrganizationId(UUID organizationId);

    @Query(
            nativeQuery = true, value = """
            WITH RECURSIVE accessible_sources AS (
                SELECT p.id, p.parent_id
                FROM project p
                INNER JOIN project_member pm ON pm.project_id = p.id
                WHERE pm.user_id = :userId
                  AND p.organization_id = :organizationId
                UNION
                SELECT c.id, c.parent_id
                FROM project c
                INNER JOIN accessible_sources s ON c.parent_id = s.id
                WHERE c.organization_id = :organizationId
            )
            SELECT DISTINCT p.id, p.name, p.parent_id, p.organization_id, p.owner_id, p.created_at
            FROM project p
            INNER JOIN accessible_sources s ON s.id = p.id
            ORDER BY p.created_at, p.name
            """
    )
    List<Project> findAccessibleProjects(
            @Param("userId") UUID userId,
            @Param("organizationId") UUID organizationId
    );
}
