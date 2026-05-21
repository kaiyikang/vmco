package com.reviewm.domain.model;

import java.util.List;

public record PromptProfile(
    String language,
    List<ReviewFocus> focuses,
    int maxContextChars
) {
    public PromptProfile {
        focuses = List.copyOf(focuses);
    }
}
