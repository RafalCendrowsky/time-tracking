package com.timetracking.auth.web.user.dto;

import com.timetracking.auth.constant.UserRole;
import jakarta.validation.constraints.NotEmpty;

import java.util.Set;

public record UpdateRolesRequest(@NotEmpty Set<UserRole> roles) {
}

