package com.reviewm.boot;

import com.reviewm.adapter.in.cli.ReviewmCommand;
import com.reviewm.adapter.out.context.java.JavaParserContextAdapter;
import com.reviewm.adapter.out.git.GitCliDiffAdapter;
import com.reviewm.adapter.out.git.GitCommandRunner;
import com.reviewm.adapter.out.progress.StderrProgressReporter;
import com.reviewm.adapter.out.template.ResourcePromptTemplateAdapter;
import com.reviewm.application.service.ContextBudgetService;
import com.reviewm.application.service.ReviewPromptPackager;

import java.util.List;

public final class DependencyFactory {
    public ReviewmCommand reviewmCommand() {
        GitCommandRunner gitCommandRunner = new GitCommandRunner();
        GitCliDiffAdapter gitDiffAdapter = new GitCliDiffAdapter(gitCommandRunner);
        ReviewPromptPackager packager = new ReviewPromptPackager(
            gitDiffAdapter,
            List.of(new JavaParserContextAdapter()),
            gitDiffAdapter,
            new ResourcePromptTemplateAdapter(),
            new ContextBudgetService(),
            new StderrProgressReporter(System.err)
        );
        return new ReviewmCommand(packager, gitDiffAdapter);
    }
}
