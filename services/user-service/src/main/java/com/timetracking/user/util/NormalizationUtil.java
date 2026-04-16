package com.timetracking.user.util;

import org.springframework.util.StringUtils;

import java.util.Locale;

public final class NormalizationUtil {
    private NormalizationUtil() {
    }

    public static String normalizeEmail(String email) {
        if (!StringUtils.hasText(email)) {
            throw new IllegalArgumentException("Email must not be blank");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
