package com.timetracking.project.model.repository;

import com.timetracking.project.model.domain.ProjectMember;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, UUID> {

    @EntityGraph(attributePaths = "user")
    Optional<ProjectMember> findByProjectIdAndUserUserId(UUID projectId, UUID userId);

    @EntityGraph(attributePaths = "user")
    List<ProjectMember> findByProjectId(UUID projectId);

    void deleteByProjectIdAndUserUserId(UUID projectId, UUID userId);

    boolean existsByProjectIdAndUserUserId(UUID projectId, UUID userId);

    int countByProjectId(UUID projectId);

    @Query(
            nativeQuery = true,
            value = """
                    WITH RECURSIVE project_chain AS (
                        SELECT id, parent_id FROM project WHERE id = :projectId
                        UNION ALL
                        SELECT p.id, p.parent_id
                        FROM project p
                        INNER JOIN project_chain ac ON p.id = ac.parent_id
                    )
                    SELECT pm.role
                    FROM project_member pm
                    INNER JOIN project_chain ac ON pm.project_id = ac.id
                    WHERE pm.user_id = :userId
                    ORDER BY CASE pm.role
                        WHEN 'ADMIN'       THEN 1
                        WHEN 'MANAGER'     THEN 2
                        WHEN 'CONTRIBUTOR' THEN 3
                        WHEN 'VIEWER'      THEN 4
                    END
                    LIMIT 1
                    """
    )
    Optional<String> findEffectiveRoleInProject(@Param("projectId") UUID projectId, @Param("userId") UUID userId);
}

