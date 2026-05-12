package com.reviewm.domain.model;

import java.util.Arrays;
import java.util.Locale;

public enum ReviewFocus {
    CORRECTNESS("correctness"),
    REGRESSION("regression"),
    TESTS("tests"),
    NULL_SAFETY("null-safety"),
    CONCURRENCY("concurrency"),
    PERFORMANCE("performance"),
    SECURITY("security"),
    API_COMPATIBILITY("api-compatibility");

    private final String cliName;

    ReviewFocus(String cliName) {
        this.cliName = cliName;
    }

    public String cliName() {
        return cliName;
    }

    public static ReviewFocus fromCliName(String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
            .filter(focus -> focus.cliName.equals(normalized))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unknown focus: " + value));
    }
}
