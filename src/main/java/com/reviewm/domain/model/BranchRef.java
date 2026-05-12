package com.reviewm.domain.model;

public record BranchRef(String name) {
    public BranchRef {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Branch name must not be blank.");
        }
    }
}
