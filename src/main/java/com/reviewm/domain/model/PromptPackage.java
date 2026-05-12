package com.reviewm.domain.model;

import java.util.List;

public record PromptPackage(
    DiffRange diffRange,
    List<ChangedFile> changedFiles,
    List<CodeContext> contexts,
    String fullDiff,
    String renderedPrompt,
    List<String> warnings
) {
    public PromptPackage {
        changedFiles = List.copyOf(changedFiles);
        contexts = List.copyOf(contexts);
        warnings = List.copyOf(warnings);
    }

    public PromptPackage withRenderedPrompt(String prompt) {
        return new PromptPackage(diffRange, changedFiles, contexts, fullDiff, prompt, warnings);
    }
}
