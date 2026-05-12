package com.reviewm.application.service;

import com.reviewm.application.command.PackageReviewPromptCommand;
import com.reviewm.application.port.in.PackageReviewPromptUseCase;
import com.reviewm.application.port.out.CodeContextPort;
import com.reviewm.application.port.out.GitDiffPort;
import com.reviewm.application.port.out.PromptTemplatePort;
import com.reviewm.domain.model.ChangedFile;
import com.reviewm.domain.model.CodeContext;
import com.reviewm.domain.model.DiffRange;
import com.reviewm.domain.model.PromptPackage;

import java.util.ArrayList;
import java.util.List;

public final class ReviewPromptPackager implements PackageReviewPromptUseCase {
    private final GitDiffPort gitDiffPort;
    private final List<CodeContextPort> contextPorts;
    private final PromptTemplatePort promptTemplatePort;
    private final ContextBudgetService contextBudgetService;

    public ReviewPromptPackager(
        GitDiffPort gitDiffPort,
        List<CodeContextPort> contextPorts,
        PromptTemplatePort promptTemplatePort,
        ContextBudgetService contextBudgetService
    ) {
        this.gitDiffPort = gitDiffPort;
        this.contextPorts = List.copyOf(contextPorts);
        this.promptTemplatePort = promptTemplatePort;
        this.contextBudgetService = contextBudgetService;
    }

    @Override
    public PromptPackage packagePrompt(PackageReviewPromptCommand command) {
        DiffRange range = gitDiffPort.resolveDiffRange(
            command.repositoryRoot(),
            command.baseBranch(),
            command.currentBranch()
        );
        List<ChangedFile> changedFiles = gitDiffPort.getChangedFiles(command.repositoryRoot(), range);
        String diff = gitDiffPort.getUnifiedDiff(command.repositoryRoot(), range);

        List<CodeContext> contexts = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (command.profile().includeJavaContext()) {
            for (ChangedFile file : changedFiles) {
                for (CodeContextPort contextPort : contextPorts) {
                    if (contextPort.supports(file)) {
                        contexts.addAll(contextPort.collect(command.repositoryRoot(), file));
                    }
                }
            }
        }
        if (command.profile().includeCallers()) {
            warnings.add("Caller context is not implemented in the first version.");
        }

        ContextBudgetResult budgetResult = contextBudgetService.limit(
            contexts,
            command.profile().maxContextChars()
        );
        warnings.addAll(budgetResult.warnings());

        PromptPackage packageData = new PromptPackage(
            range,
            changedFiles,
            budgetResult.contexts(),
            diff,
            "",
            warnings
        );
        return packageData.withRenderedPrompt(promptTemplatePort.render(packageData, command.profile()));
    }
}
