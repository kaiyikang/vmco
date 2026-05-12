package com.reviewm.domain.model;

import java.util.List;

public record ChangedFile(
    String path,
    String oldPath,
    ChangeType changeType,
    String language,
    List<DiffHunk> hunks
) {
    public ChangedFile {
        hunks = List.copyOf(hunks);
    }
}
