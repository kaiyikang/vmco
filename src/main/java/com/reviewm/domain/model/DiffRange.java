package com.reviewm.domain.model;

public record DiffRange(
    String baseBranch,
    String currentBranch,
    String mergeBaseCommit,
    String headCommit
) {
}
