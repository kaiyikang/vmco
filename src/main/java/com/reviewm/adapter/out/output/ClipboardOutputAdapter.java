package com.reviewm.adapter.out.output;

import com.reviewm.application.port.out.PromptOutputPort;
import com.reviewm.shared.ReviewmException;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class ClipboardOutputAdapter implements PromptOutputPort {
    @Override
    public void write(String content) {
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        if (os.contains("mac")) {
            writeToCommand(content, "pbcopy");
            return;
        }
        if (os.contains("win")) {
            writeToCommand(content, "powershell.exe", "-NoProfile", "-Command", "Set-Clipboard");
            return;
        }
        if (commandAvailable("wl-copy")) {
            writeToCommand(content, "wl-copy");
            return;
        }
        if (commandAvailable("xclip")) {
            writeToCommand(content, "xclip", "-selection", "clipboard");
            return;
        }
        if (commandAvailable("xsel")) {
            writeToCommand(content, "xsel", "--clipboard", "--input");
            return;
        }
        throw new ReviewmException("No supported clipboard command found. Use --console or --output file.");
    }

    private void writeToCommand(String content, String... command) {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        try {
            Process process = processBuilder.start();
            try (OutputStreamWriter writer = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8)) {
                writer.write(content);
            }
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                String error = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
                throw new ReviewmException("Clipboard command failed: "
                    + String.join(" ", command)
                    + "\n" + output + error);
            }
        } catch (IOException e) {
            throw new ReviewmException("Unable to write to clipboard.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ReviewmException("Clipboard write interrupted.", e);
        }
    }

    private boolean commandAvailable(String command) {
        try {
            Process process = new ProcessBuilder("sh", "-c", "command -v " + command).start();
            return process.waitFor() == 0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
