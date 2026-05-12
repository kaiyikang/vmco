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
        String template = loadTemplate(profile.templateName());
        return template
            .replace("{{language}}", profile.language())
            .replace("{{focuses}}", renderFocuses(profile))
            .replace("{{baseBranch}}", promptPackage.diffRange().baseBranch())
            .replace("{{currentBranch}}", promptPackage.diffRange().currentBranch())
            .replace("{{mergeBaseCommit}}", promptPackage.diffRange().mergeBaseCommit())
            .replace("{{headCommit}}", promptPackage.diffRange().headCommit())
            .replace("{{includesWorkingTree}}", Boolean.toString(promptPackage.diffRange().compareWithWorkingTree()))
            .replace("{{changedFiles}}", renderChangedFiles(promptPackage))
            .replace("{{contexts}}", renderContexts(promptPackage))
            .replace("{{diff}}", promptPackage.fullDiff());
    }

    private String loadTemplate(String name) {
        String resolvedName = (name == null || name.isBlank()) ? DEFAULT_TEMPLATE : name;
        String resourceName = "prompts/" + resolvedName + ".md";
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        try (InputStream inputStream = classLoader.getResourceAsStream(resourceName)) {
            if (inputStream == null) {
                throw new ReviewmException("Prompt template not found: " + resolvedName);
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ReviewmException("Unable to read prompt template: " + resolvedName, e);
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
        StringBuilder builder = new StringBuilder();
        if (promptPackage.contexts().isEmpty()) {
            builder.append("- No extra code context collected.");
        } else {
            for (CodeContext context : promptPackage.contexts()) {
                builder.append("### ")
                    .append(context.symbolType())
                    .append(" ")
                    .append(context.filePath())
                    .append(" :: ")
                    .append(context.symbolName())
                    .append(System.lineSeparator())
                    .append("Reason: ")
                    .append(context.reason())
                    .append(System.lineSeparator())
                    .append("```java")
                    .append(System.lineSeparator())
                    .append(context.content())
                    .append(System.lineSeparator())
                    .append("```")
                    .append(System.lineSeparator())
                    .append(System.lineSeparator());
            }
        }
        if (!promptPackage.warnings().isEmpty()) {
            builder.append(System.lineSeparator()).append("Warnings:").append(System.lineSeparator());
            for (String warning : promptPackage.warnings()) {
                builder.append("- ").append(warning).append(System.lineSeparator());
            }
        }
        return builder.toString().stripTrailing();
    }
}
