package com.reviewm.application.port.out;

import java.nio.file.Path;

public interface RepositoryRootPort {
    Path resolveRepositoryRoot(Path cwd);
}
