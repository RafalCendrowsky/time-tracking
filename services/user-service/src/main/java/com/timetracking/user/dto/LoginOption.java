package com.timetracking.user.dto;

import com.timetracking.user.constant.LoginOptionType;

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

