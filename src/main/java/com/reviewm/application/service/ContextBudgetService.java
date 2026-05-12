package com.reviewm.application.service;

import com.reviewm.domain.model.CodeContext;

import java.util.ArrayList;
import java.util.List;

public final class ContextBudgetService {
    private static final int MIN_TRIMMED_CONTEXT_CHARS = 400;

    public ContextBudgetResult limit(List<CodeContext> contexts, int maxChars) {
        List<CodeContext> accepted = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        int remaining = maxChars;

        for (CodeContext context : contexts) {
            int contextLength = context.content().length();
            if (contextLength <= remaining) {
                accepted.add(context);
                remaining -= contextLength;
                continue;
            }

            if (remaining >= MIN_TRIMMED_CONTEXT_CHARS) {
                String trimmed = context.content().substring(0, remaining)
                    + "\n\n... context truncated by reviewm ...";
                accepted.add(context.withContent(trimmed));
                warnings.add("Context truncated at " + context.filePath() + " " + context.symbolName() + ".");
            } else {
                warnings.add("Context budget reached before adding " + context.filePath() + " " + context.symbolName() + ".");
            }
            break;
        }

        return new ContextBudgetResult(accepted, warnings);
    }
}
