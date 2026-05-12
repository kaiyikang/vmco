package com.reviewm.application.port.out;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public interface SourceFilePort {
    Optional<List<String>> readLines(Path repositoryRoot, String ref, String path);
}
