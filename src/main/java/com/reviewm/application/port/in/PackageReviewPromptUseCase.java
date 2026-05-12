package com.reviewm.application.port.in;

import com.reviewm.application.command.PackageReviewPromptCommand;
import com.reviewm.domain.model.PromptPackage;

public interface PackageReviewPromptUseCase {
    PromptPackage packagePrompt(PackageReviewPromptCommand command);
}
