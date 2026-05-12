package com.reviewm.application.service;

import com.reviewm.domain.model.CodeContext;

import java.util.List;

public record ContextBudgetResult(List<CodeContext> contexts, List<String> warnings) {
    public ContextBudgetResult {
        contexts = List.copyOf(contexts);
        warnings = List.copyOf(warnings);
    }
}
