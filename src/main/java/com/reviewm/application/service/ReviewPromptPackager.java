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
import java.util.Optional;

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

        List<String> warnings = new ArrayList<>();
        List<CodeContext> contexts = collectContexts(command, range, changedFiles, warnings);
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

    private List<CodeContext> collectContexts(
        PackageReviewPromptCommand command,
        DiffRange range,
        List<ChangedFile> changedFiles,
        List<String> warnings
    ) {
        if (!command.profile().includeJavaContext()) {
            return List.of();
        }

        progressReporter.info("Collecting Java context...");
        List<CodeContext> contexts = new ArrayList<>();
        for (ChangedFile file : changedFiles) {
            contexts.addAll(collectFileContexts(command, range, file, warnings));
        }
        return contexts;
    }

    private List<CodeContext> collectFileContexts(
        PackageReviewPromptCommand command,
        DiffRange range,
        ChangedFile file,
        List<String> warnings
    ) {
        List<CodeContextPort> supportedPorts = supportedContextPorts(file);
        if (supportedPorts.isEmpty()) {
            return List.of();
        }

        Optional<List<String>> sourceLines = sourceFilePort.readLines(
            command.repositoryRoot(),
            range.currentBranch(),
            file.path()
        );
        if (sourceLines.isEmpty()) {
            warnings.add("Unable to read committed source for context: " + file.path());
            return List.of();
        }

        List<CodeContext> contexts = new ArrayList<>();
        for (CodeContextPort contextPort : supportedPorts) {
            contexts.addAll(contextPort.collect(file, sourceLines.get()));
        }
        return contexts;
    }

    private List<CodeContextPort> supportedContextPorts(ChangedFile file) {
        return contextPorts.stream()
            .filter(contextPort -> contextPort.supports(file))
            .toList();
    }
}
