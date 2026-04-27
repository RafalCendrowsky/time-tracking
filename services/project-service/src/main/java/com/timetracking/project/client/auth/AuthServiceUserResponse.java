package com.timetracking.project.client.auth;

import java.util.UUID;

public record AuthServiceUserResponse(
        UUID id,
        String email,
        String firstName,
        String lastName,
        UUID organizationId
) {
}

