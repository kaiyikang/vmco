package com.reviewm.domain.model;

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
}
