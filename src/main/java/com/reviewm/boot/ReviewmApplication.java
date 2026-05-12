package com.reviewm.boot;

public final class ReviewmApplication {
    private ReviewmApplication() {
    }

    public static void main(String[] args) {
        int exitCode = new DependencyFactory().reviewmCommand().execute(args);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }
}
