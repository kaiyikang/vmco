package com.reviewm.domain.model;

import java.util.List;

public record DiffHunk(
    int oldStart,
    int oldLines,
    int newStart,
    int newLines,
    String header,
    List<String> lines
) {
    public DiffHunk {
        lines = List.copyOf(lines);
    }

    public int newEndInclusive() {
        return newStart + Math.max(newLines, 1) - 1;
    }
}
