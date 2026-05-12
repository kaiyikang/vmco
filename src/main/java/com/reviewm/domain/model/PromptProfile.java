package com.reviewm.domain.model;

import java.util.List;

public record PromptProfile(
    String templateName,
    String language,
    List<ReviewFocus> focuses,
    int maxContextChars,
    boolean includeJavaContext,
    boolean includeCallers
) {
    public PromptProfile {
        focuses = List.copyOf(focuses);
    }
}
