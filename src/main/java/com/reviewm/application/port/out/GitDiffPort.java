package com.reviewm.application.port.out;

import com.reviewm.domain.model.ChangedFile;
import com.reviewm.domain.model.DiffRange;

import java.nio.file.Path;
import java.util.List;

public interface GitDiffPort {
    void refreshBase(Path repositoryRoot, String baseBranch);

    DiffRange resolveDiffRange(Path repositoryRoot, String baseBranch, String currentBranch);

    List<ChangedFile> getChangedFiles(Path repositoryRoot, DiffRange range);

    String getUnifiedDiff(Path repositoryRoot, DiffRange range);
}
