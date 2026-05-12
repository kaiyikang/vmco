package com.reviewm.domain.policy;

public final class ContextBudgetPolicy {
    private static final int MIN_CONTEXT_CHARS = 1_000;
    private static final int DEFAULT_CONTEXT_CHARS = 16_000;

    private ContextBudgetPolicy() {
    }

    public static int defaultContextChars() {
        return DEFAULT_CONTEXT_CHARS;
    }

    public static int normalize(int requested) {
        if (requested <= 0) {
            return DEFAULT_CONTEXT_CHARS;
        }
        return Math.max(requested, MIN_CONTEXT_CHARS);
    }
}
