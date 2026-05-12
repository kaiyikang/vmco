package com.reviewm.adapter.out.progress;

import com.reviewm.application.port.out.ProgressReporter;

import java.io.PrintStream;

public final class StderrProgressReporter implements ProgressReporter {
    private final PrintStream err;

    public StderrProgressReporter(PrintStream err) {
        this.err = err;
    }

    @Override
    public void info(String message) {
        err.println("reviewm: " + message);
    }
}
