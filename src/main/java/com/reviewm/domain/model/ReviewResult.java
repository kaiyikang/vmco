package com.reviewm.domain.model;

import java.util.List;

public record ReviewResult(String provider, String content, List<String> warnings) {
    public ReviewResult {
        warnings = List.copyOf(warnings);
    }
}
