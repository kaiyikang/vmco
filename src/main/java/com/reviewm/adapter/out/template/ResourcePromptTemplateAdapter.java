package com.reviewm.adapter.out.template;

import com.reviewm.application.port.out.PromptTemplatePort;
import com.reviewm.domain.model.ChangedFile;
import com.reviewm.domain.model.CodeContext;
import com.reviewm.domain.model.PromptPackage;
import com.reviewm.domain.model.PromptProfile;
import com.reviewm.domain.model.ReviewFocus;
import com.reviewm.shared.ReviewmException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

public final class ResourcePromptTemplateAdapter implements PromptTemplatePort {
    private static final String DEFAULT_TEMPLATE = "default-review";

    @Override
    public String render(PromptPackage promptPackage, PromptProfile profile) {
        String template = loadTemplate();
        return template
            .replace("{{language}}", profile.language())
            .replace("{{focuses}}", renderFocuses(profile))
            .replace("{{baseBranch}}", promptPackage.diffRange().baseBranch())
            .replace("{{currentBranch}}", promptPackage.diffRange().currentBranch())
            .replace("{{mergeBaseCommit}}", promptPackage.diffRange().mergeBaseCommit())
            .replace("{{headCommit}}", promptPackage.diffRange().headCommit())
            .replace("{{changedFiles}}", renderChangedFiles(promptPackage))
            .replace("{{contexts}}", renderContexts(promptPackage))
            .replace("{{diff}}", promptPackage.fullDiff());
    }

    private String loadTemplate() {
        String resourceName = "prompts/" + DEFAULT_TEMPLATE + ".md";
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        try (InputStream inputStream = classLoader.getResourceAsStream(resourceName)) {
            if (inputStream == null) {
                throw new ReviewmException("Prompt template not found: " + DEFAULT_TEMPLATE);
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ReviewmException("Unable to read prompt template: " + DEFAULT_TEMPLATE, e);
        }
    }

    private String renderFocuses(PromptProfile profile) {
        return profile.focuses().stream()
            .map(ReviewFocus::cliName)
            .map(focus -> "- " + focus)
            .collect(Collectors.joining(System.lineSeparator()));
    }

    private String renderChangedFiles(PromptPackage promptPackage) {
        if (promptPackage.changedFiles().isEmpty()) {
            return "- No changed files.";
        }
        return promptPackage.changedFiles().stream()
            .map(this::renderChangedFile)
            .collect(Collectors.joining(System.lineSeparator()));
    }

    private String renderChangedFile(ChangedFile file) {
        String oldPath = file.oldPath() == null ? "" : " from " + file.oldPath();
        return "- " + file.changeType() + " " + file.path() + oldPath
            + " (" + file.language() + ", hunks=" + file.hunks().size() + ")";
    }

    private String renderContexts(PromptPackage promptPackage) {
        return (renderContextBody(promptPackage) + renderWarnings(promptPackage)).stripTrailing();
    }

    private String renderContextBody(PromptPackage promptPackage) {
        if (promptPackage.contexts().isEmpty()) {
            return "- No extra code context collected.";
        }
        return promptPackage.contexts().stream()
            .map(this::renderContext)
            .collect(Collectors.joining(System.lineSeparator() + System.lineSeparator()));
    }

    private String renderContext(CodeContext context) {
        return "### " + context.symbolType() + " " + context.filePath() + " :: " + context.symbolName()
            + System.lineSeparator()
            + "Reason: " + context.reason()
            + System.lineSeparator()
            + "```java"
            + System.lineSeparator()
            + context.content()
            + System.lineSeparator()
            + "```";
    }

    private String renderWarnings(PromptPackage promptPackage) {
        if (promptPackage.warnings().isEmpty()) {
            return "";
        }
        return System.lineSeparator()
            + System.lineSeparator()
            + "Warnings:"
            + System.lineSeparator()
            + promptPackage.warnings().stream()
                .map(warning -> "- " + warning)
                .collect(Collectors.joining(System.lineSeparator()));
    }
}
