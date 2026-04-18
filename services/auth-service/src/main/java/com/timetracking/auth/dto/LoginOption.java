package com.timetracking.auth.dto;

import com.timetracking.auth.constant.LoginOptionType;

public record LoginOption(
        LoginOptionType type,
        String label,
        String organizationId,
        String organizationName,
        String loginUrl
) {

    public static LoginOption internal(String loginUrl) {
        return new LoginOption(LoginOptionType.INTERNAL, "Internal form login", null, null, loginUrl);
    }

    public static LoginOption external(String organizationId, String organizationName, String loginUrl) {
        return new LoginOption(LoginOptionType.EXTERNAL, organizationName, organizationId, organizationName, loginUrl);
    }
}

