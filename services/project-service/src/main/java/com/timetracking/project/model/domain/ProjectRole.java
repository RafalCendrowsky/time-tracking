package com.timetracking.project.model.domain;

public enum ProjectRole {
    ADMIN,
    MANAGER,
    CONTRIBUTOR,
    VIEWER;

    public boolean canManageAdminOrManager() {
        return this == ADMIN;
    }

    public boolean canManageContributorOrViewer() {
        return this == ADMIN || this == MANAGER;
    }

    public boolean canManageSubprojects() {
        return this == ADMIN || this == MANAGER;
    }
}
