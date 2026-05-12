package com.reviewm.adapter.out.git;

import com.reviewm.application.port.out.GitDiffPort;
import com.reviewm.application.port.out.RepositoryRootPort;
import com.reviewm.application.port.out.SourceFilePort;
import com.reviewm.domain.model.ChangeType;
import com.reviewm.domain.model.ChangedFile;
import com.reviewm.domain.model.DiffHunk;
import com.reviewm.domain.model.DiffRange;
import com.reviewm.shared.ReviewmException;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GitCliDiffAdapter implements GitDiffPort, RepositoryRootPort, SourceFilePort {
    private static final List<String> BASE_BRANCH_CANDIDATES = List.of(
        "origin/main",
        "origin/master",
        "main",
        "master"
    );
    private static final Pattern HUNK_HEADER = Pattern.compile(
        "^@@ -(\\d+)(?:,(\\d+))? \\+(\\d+)(?:,(\\d+))? @@.*$"
    );

    private final GitCommandRunner git;

    public GitCliDiffAdapter(GitCommandRunner git) {
        this.git = git;
    }

    @Override
    public Path resolveRepositoryRoot(Path cwd) {
        String root = git.run(cwd, "rev-parse", "--show-toplevel");
        return Path.of(root).toAbsolutePath().normalize();
    }

    @Override
    public void refreshBase(Path repositoryRoot, String baseBranch) {
        if (!remoteExists(repositoryRoot, "origin")) {
            return;
        }
        git.run(repositoryRoot, "fetch", "--prune", "origin");
    }

    @Override
    public DiffRange resolveDiffRange(Path repositoryRoot, String baseBranch, String currentBranch) {
        String resolvedBase = resolveBaseBranch(repositoryRoot, baseBranch);
        String resolvedCurrent = resolveCurrentBranch(repositoryRoot, currentBranch);
        String headCommit = git.run(repositoryRoot, "rev-parse", resolvedCurrent);
        String mergeBase = git.run(repositoryRoot, "merge-base", resolvedBase, resolvedCurrent);
        return new DiffRange(resolvedBase, resolvedCurrent, mergeBase, headCommit);
    }

    @Override
    public List<ChangedFile> getChangedFiles(Path repositoryRoot, DiffRange range) {
        String output = git.run(
            repositoryRoot,
            "diff",
            "--name-status",
            "--find-renames",
            range.mergeBaseCommit(),
            range.currentBranch()
        );
        if (output.isBlank()) {
            return List.of();
        }

        List<ChangedFile> files = new ArrayList<>();
        for (String line : output.split("\\R")) {
            if (line.isBlank()) {
                continue;
            }
            String[] parts = line.split("\\t");
            if (parts.length < 2) {
                continue;
            }
            String status = parts[0];
            ChangeType changeType = toChangeType(status);
            String oldPath = null;
            String path = parts[1];
            if ((changeType == ChangeType.RENAMED || changeType == ChangeType.COPIED) && parts.length >= 3) {
                oldPath = parts[1];
                path = parts[2];
            }
            files.add(new ChangedFile(path, oldPath, changeType, languageFor(path), hunksFor(repositoryRoot, range, path)));
        }
        return files;
    }

    @Override
    public String getUnifiedDiff(Path repositoryRoot, DiffRange range) {
        return git.run(
            repositoryRoot,
            "diff",
            "--find-renames",
            "--unified=80",
            range.mergeBaseCommit(),
            range.currentBranch()
        );
    }

    @Override
    public Optional<List<String>> readLines(Path repositoryRoot, String ref, String path) {
        GitCommandRunner.CommandResult result = git.runAllowingFailure(
            repositoryRoot,
            "show",
            ref + ":" + path
        );
        if (result.exitCode() != 0) {
            return Optional.empty();
        }
        return Optional.of(Arrays.asList(result.output().split("\\R", -1)));
    }

    private String resolveBaseBranch(Path repositoryRoot, String requestedBase) {
        if (requestedBase != null && !requestedBase.isBlank() && !"auto".equalsIgnoreCase(requestedBase)) {
            ensureRefExists(repositoryRoot, requestedBase);
            return requestedBase;
        }
        for (String candidate : BASE_BRANCH_CANDIDATES) {
            if (refExists(repositoryRoot, candidate)) {
                return candidate;
            }
        }
        throw new ReviewmException("Unable to find a base branch. Tried: "
            + String.join(", ", BASE_BRANCH_CANDIDATES)
            + ". Pass one explicitly with --base <branch>.");
    }

    private String resolveCurrentBranch(Path repositoryRoot, String requestedCurrent) {
        if (requestedCurrent != null && !requestedCurrent.isBlank()) {
            ensureRefExists(repositoryRoot, requestedCurrent);
            return requestedCurrent;
        }
        String branch = git.run(repositoryRoot, "rev-parse", "--abbrev-ref", "HEAD");
        if ("HEAD".equals(branch)) {
            return "HEAD";
        }
        return branch;
    }

    private void ensureRefExists(Path repositoryRoot, String ref) {
        if (!refExists(repositoryRoot, ref)) {
            throw new ReviewmException("Git ref does not exist: " + ref);
        }
    }

    private boolean refExists(Path repositoryRoot, String ref) {
        return git.runAllowingFailure(
            repositoryRoot,
            "rev-parse",
            "--verify",
            "--quiet",
            ref + "^{commit}"
        ).exitCode() == 0;
    }

    private boolean remoteExists(Path repositoryRoot, String remote) {
        return git.runAllowingFailure(repositoryRoot, "remote", "get-url", remote).exitCode() == 0;
    }

    private List<DiffHunk> hunksFor(Path repositoryRoot, DiffRange range, String path) {
        GitCommandRunner.CommandResult result = git.runAllowingFailure(
            repositoryRoot,
            "diff",
            "--unified=0",
            "--no-ext-diff",
            range.mergeBaseCommit(),
            range.currentBranch(),
            "--",
            path
        );
        if (result.exitCode() != 0 || result.output().isBlank()) {
            return List.of();
        }
        return parseHunks(result.output());
    }

    private List<DiffHunk> parseHunks(String patch) {
        List<DiffHunk> hunks = new ArrayList<>();
        HunkBuilder current = null;
        for (String line : patch.split("\\R")) {
            Matcher matcher = HUNK_HEADER.matcher(line);
            if (matcher.matches()) {
                if (current != null) {
                    hunks.add(current.build());
                }
                current = new HunkBuilder(
                    parseInt(matcher.group(1), 0),
                    parseInt(matcher.group(2), 1),
                    parseInt(matcher.group(3), 0),
                    parseInt(matcher.group(4), 1),
                    line
                );
            } else if (current != null) {
                current.lines.add(line);
            }
        }
        if (current != null) {
            hunks.add(current.build());
        }
        return hunks;
    }

    private int parseInt(String value, int defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Integer.parseInt(value);
    }

    private ChangeType toChangeType(String status) {
        return switch (status.charAt(0)) {
            case 'A' -> ChangeType.ADDED;
            case 'M' -> ChangeType.MODIFIED;
            case 'D' -> ChangeType.DELETED;
            case 'R' -> ChangeType.RENAMED;
            case 'C' -> ChangeType.COPIED;
            case 'T' -> ChangeType.TYPE_CHANGED;
            default -> ChangeType.UNKNOWN;
        };
    }

    private String languageFor(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".java")) {
            return "java";
        }
        if (lower.endsWith(".xml")) {
            return "xml";
        }
        if (lower.endsWith(".yml") || lower.endsWith(".yaml")) {
            return "yaml";
        }
        if (lower.endsWith(".properties")) {
            return "properties";
        }
        return "text";
    }

    private static final class HunkBuilder {
        private final int oldStart;
        private final int oldLines;
        private final int newStart;
        private final int newLines;
        private final String header;
        private final List<String> lines = new ArrayList<>();

        private HunkBuilder(int oldStart, int oldLines, int newStart, int newLines, String header) {
            this.oldStart = oldStart;
            this.oldLines = oldLines;
            this.newStart = newStart;
            this.newLines = newLines;
            this.header = header;
        }

        private DiffHunk build() {
            return new DiffHunk(oldStart, oldLines, newStart, newLines, header, lines);
        }
    }
}
