package com.reviewm.adapter.out.output;

import com.reviewm.application.port.out.PromptOutputPort;

import java.io.PrintStream;

public final class ConsoleOutputAdapter implements PromptOutputPort {
    private final PrintStream out;

    public ConsoleOutputAdapter(PrintStream out) {
        this.out = out;
    }

    @Override
    public void write(String content) {
        out.println(content);
    }
}
