package com.reviewm.adapter.in.cli;

import com.reviewm.adapter.out.output.ClipboardOutputAdapter;
import com.reviewm.application.command.PackageReviewPromptCommand;
import com.reviewm.application.port.in.PackageReviewPromptUseCase;
import com.reviewm.application.port.out.PromptOutputPort;
import com.reviewm.application.port.out.RepositoryRootPort;
import com.reviewm.domain.model.PromptPackage;
import com.reviewm.domain.model.PromptProfile;
import com.reviewm.domain.model.ReviewFocus;
import com.reviewm.domain.policy.ContextBudgetPolicy;
import com.reviewm.shared.ReviewmException;

import java.nio.file.Path;
import java.util.List;

public final class ReviewmCommand {
    private static final String DEFAULT_LANGUAGE = "English";
    private static final List<ReviewFocus> DEFAULT_FOCUSES = List.of(
        ReviewFocus.CORRECTNESS,
        ReviewFocus.REGRESSION,
        ReviewFocus.TESTS,
        ReviewFocus.NULL_SAFETY
    );

    private final PackageReviewPromptUseCase packageReviewPromptUseCase;
    private final RepositoryRootPort repositoryRootPort;

    public ReviewmCommand(
        PackageReviewPromptUseCase packageReviewPromptUseCase,
        RepositoryRootPort repositoryRootPort
    ) {
        this.packageReviewPromptUseCase = packageReviewPromptUseCase;
        this.repositoryRootPort = repositoryRootPort;
    }

    public int execute(String[] args) {
        try {
            rejectArguments(args);

            Path repositoryRoot = repositoryRootPort.resolveRepositoryRoot(Path.of("").toAbsolutePath());
            System.err.println("reviewm: using repository " + repositoryRoot);
            PromptProfile profile = new PromptProfile(
                DEFAULT_LANGUAGE,
                DEFAULT_FOCUSES,
                ContextBudgetPolicy.defaultContextChars()
            );
            PromptPackage promptPackage = packageReviewPromptUseCase.packagePrompt(
                new PackageReviewPromptCommand(repositoryRoot, profile)
            );

            PromptOutputPort output = new ClipboardOutputAdapter();
            System.err.println("reviewm: writing prompt to clipboard...");
            output.write(promptPackage.renderedPrompt());
            printSummary(promptPackage);
            return 0;
        } catch (IllegalArgumentException | ReviewmException e) {
            System.err.println("reviewm: " + e.getMessage());
            return 2;
        }
    }

    private void rejectArguments(String[] args) {
        if (args.length > 0) {
            throw new IllegalArgumentException("reviewm does not accept arguments. Run ./bin/reviewm.");
        }
    }

    private void printSummary(PromptPackage promptPackage) {
        System.err.println("reviewm: prompt written to clipboard"
            + " (" + promptPackage.renderedPrompt().length() + " chars, "
            + promptPackage.changedFiles().size() + " files, "
            + promptPackage.contexts().size() + " contexts).");
        for (String warning : promptPackage.warnings()) {
            System.err.println("reviewm warning: " + warning);
        }
    }
}
