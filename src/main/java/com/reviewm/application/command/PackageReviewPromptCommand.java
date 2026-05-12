package com.reviewm.application.command;

import com.reviewm.domain.model.PromptProfile;

import java.nio.file.Path;

public record PackageReviewPromptCommand(
    Path repositoryRoot,
    String baseBranch,
    String currentBranch,
    PromptProfile profile
) {
}
