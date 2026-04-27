package com.timetracking.project.model.repository;

import com.timetracking.project.model.domain.UserProjection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserProjectionRepository extends JpaRepository<UserProjection, UUID> {
}

