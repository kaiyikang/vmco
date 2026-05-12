package com.reviewm.adapter.out.output;

import com.reviewm.application.port.out.PromptOutputPort;
import com.reviewm.shared.ReviewmException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class FileOutputAdapter implements PromptOutputPort {
    private final Path path;

    public FileOutputAdapter(Path path) {
        this.path = path;
    }

    @Override
    public void write(String content) {
        try {
            Path parent = path.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(path, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ReviewmException("Unable to write prompt file: " + path, e);
        }
    }
}
