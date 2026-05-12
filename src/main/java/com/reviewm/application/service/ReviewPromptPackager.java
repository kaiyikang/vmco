package com.reviewm.application.service;

import com.reviewm.application.command.PackageReviewPromptCommand;
import com.reviewm.application.port.in.PackageReviewPromptUseCase;
import com.reviewm.application.port.out.CodeContextPort;
import com.reviewm.application.port.out.GitDiffPort;
import com.reviewm.application.port.out.ProgressReporter;
import com.reviewm.application.port.out.PromptTemplatePort;
import com.reviewm.application.port.out.SourceFilePort;
import com.reviewm.domain.model.ChangedFile;
import com.reviewm.domain.model.CodeContext;
import com.reviewm.domain.model.DiffRange;
import com.reviewm.domain.model.PromptPackage;

import java.util.ArrayList;
import java.util.List;

public final class ReviewPromptPackager implements PackageReviewPromptUseCase {
    private final GitDiffPort gitDiffPort;
    private final List<CodeContextPort> contextPorts;
    private final SourceFilePort sourceFilePort;
    private final PromptTemplatePort promptTemplatePort;
    private final ContextBudgetService contextBudgetService;
    private final ProgressReporter progressReporter;

    public ReviewPromptPackager(
        GitDiffPort gitDiffPort,
        List<CodeContextPort> contextPorts,
        SourceFilePort sourceFilePort,
        PromptTemplatePort promptTemplatePort,
        ContextBudgetService contextBudgetService,
        ProgressReporter progressReporter
    ) {
        this.gitDiffPort = gitDiffPort;
        this.contextPorts = List.copyOf(contextPorts);
        this.sourceFilePort = sourceFilePort;
        this.promptTemplatePort = promptTemplatePort;
        this.contextBudgetService = contextBudgetService;
        this.progressReporter = progressReporter;
    }

    @Override
    public PromptPackage packagePrompt(PackageReviewPromptCommand command) {
        progressReporter.info("Fetching latest base branch...");
        gitDiffPort.refreshBase(command.repositoryRoot(), command.baseBranch());

        progressReporter.info("Resolving diff range...");
        DiffRange range = gitDiffPort.resolveDiffRange(
            command.repositoryRoot(),
            command.baseBranch(),
            command.currentBranch()
        );
        progressReporter.info("Collecting changed files...");
        List<ChangedFile> changedFiles = gitDiffPort.getChangedFiles(command.repositoryRoot(), range);
        progressReporter.info("Collecting unified diff...");
        String diff = gitDiffPort.getUnifiedDiff(command.repositoryRoot(), range);

        List<CodeContext> contexts = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (command.profile().includeJavaContext()) {
            progressReporter.info("Collecting Java context...");
            for (ChangedFile file : changedFiles) {
                for (CodeContextPort contextPort : contextPorts) {
                    if (contextPort.supports(file)) {
                        sourceFilePort.readLines(command.repositoryRoot(), range.currentBranch(), file.path())
                            .ifPresentOrElse(
                                lines -> contexts.addAll(contextPort.collect(file, lines)),
                                () -> warnings.add("Unable to read committed source for context: " + file.path())
                            );
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

        progressReporter.info("Rendering review prompt...");
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
