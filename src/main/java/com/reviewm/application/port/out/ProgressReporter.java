package com.reviewm.application.port.out;

public interface ProgressReporter {
    void info(String message);

    static ProgressReporter noop() {
        return message -> {
        };
    }
}
