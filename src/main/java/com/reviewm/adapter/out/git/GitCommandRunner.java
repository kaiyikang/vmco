package com.reviewm.adapter.out.git;

import com.reviewm.shared.ReviewmException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public final class GitCommandRunner {
    private static final Duration TIMEOUT = Duration.ofMinutes(2);

    public String run(Path workingDirectory, String... args) {
        CommandResult result = runAllowingFailure(workingDirectory, args);
        if (result.exitCode() != 0) {
            throw new ReviewmException("Git command failed: git " + String.join(" ", args)
                + "\n" + result.output());
        }
        return result.output().stripTrailing();
    }

    public CommandResult runAllowingFailure(Path workingDirectory, String... args) {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(args));

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(workingDirectory.toFile());
        processBuilder.redirectErrorStream(true);

        try {
            Process process = processBuilder.start();
            CompletableFuture<String> outputFuture = CompletableFuture.supplyAsync(() -> readOutput(process));
            boolean finished = process.waitFor(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new ReviewmException("Git command timed out: git " + String.join(" ", args));
            }
            String output = outputFuture.get();
            return new CommandResult(process.exitValue(), output.stripTrailing());
        } catch (IOException e) {
            throw new ReviewmException("Unable to run git. Make sure git is installed.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ReviewmException("Git command interrupted.", e);
        } catch (ExecutionException e) {
            throw new ReviewmException("Unable to read git output.", e);
        }
    }

    private String readOutput(Process process) {
        try {
            return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ReviewmException("Unable to read git output.", e);
        }
    }

    public record CommandResult(int exitCode, String output) {
    }
}
