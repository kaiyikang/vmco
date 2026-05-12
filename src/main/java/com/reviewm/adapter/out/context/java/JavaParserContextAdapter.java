package com.reviewm.adapter.out.context.java;

import com.reviewm.application.port.out.CodeContextPort;
import com.reviewm.domain.model.ChangeType;
import com.reviewm.domain.model.ChangedFile;
import com.reviewm.domain.model.CodeContext;
import com.reviewm.domain.model.DiffHunk;
import com.reviewm.domain.model.SymbolType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class JavaParserContextAdapter implements CodeContextPort {
    private static final Pattern TYPE_PATTERN = Pattern.compile(
        "\\b(class|interface|enum|record)\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\b"
    );
    private static final Pattern METHOD_NAME_PATTERN = Pattern.compile(
        "([A-Za-z_$][A-Za-z0-9_$]*)\\s*\\([^;{}]*\\)\\s*(?:throws\\s+[^{]+)?\\{\\s*$"
    );

    @Override
    public boolean supports(ChangedFile file) {
        return "java".equals(file.language()) && file.changeType() != ChangeType.DELETED;
    }

    @Override
    public List<CodeContext> collect(Path repositoryRoot, ChangedFile file) {
        Path source = repositoryRoot.resolve(file.path()).normalize();
        if (!Files.isRegularFile(source)) {
            return List.of();
        }
        try {
            List<String> lines = Files.readAllLines(source, StandardCharsets.UTF_8);
            List<SymbolRange> symbols = scanSymbols(lines);
            Map<String, CodeContext> contexts = new LinkedHashMap<>();
            for (DiffHunk hunk : file.hunks()) {
                int start = Math.max(1, hunk.newStart());
                int end = Math.max(start, hunk.newEndInclusive());
                Optional<SymbolRange> method = deepestSymbolContaining(symbols, start, end, SymbolType.METHOD);
                Optional<SymbolRange> type = deepestSymbolContaining(symbols, start, end, SymbolType.CLASS);
                SymbolRange selected = method.or(() -> type).orElseGet(() -> fileWindow(lines, start, end));
                String key = selected.type + ":" + selected.name + ":" + selected.startLine + ":" + selected.endLine;
                contexts.putIfAbsent(key, new CodeContext(
                    file.path(),
                    selected.name,
                    selected.type,
                    "Changed lines " + start + "-" + end,
                    extract(lines, selected.startLine, selected.endLine)
                ));
            }
            return new ArrayList<>(contexts.values());
        } catch (IOException e) {
            return List.of();
        }
    }

    private List<SymbolRange> scanSymbols(List<String> lines) {
        List<SymbolRange> ranges = new ArrayList<>();
        for (int index = 0; index < lines.size(); index++) {
            String line = stripLineComment(lines.get(index)).strip();
            if (line.isBlank()) {
                continue;
            }
            Matcher typeMatcher = TYPE_PATTERN.matcher(line);
            if (typeMatcher.find()) {
                int start = index + 1;
                int end = findBlockEnd(lines, index);
                ranges.add(new SymbolRange(typeMatcher.group(2), SymbolType.CLASS, start, end));
                continue;
            }
            if (looksLikeMethodDeclaration(line)) {
                Matcher methodMatcher = METHOD_NAME_PATTERN.matcher(line);
                if (methodMatcher.find()) {
                    int start = includeLeadingAnnotations(lines, index) + 1;
                    int end = findBlockEnd(lines, index);
                    ranges.add(new SymbolRange(methodMatcher.group(1), SymbolType.METHOD, start, end));
                }
            }
        }
        return ranges;
    }

    private boolean looksLikeMethodDeclaration(String line) {
        if (!line.contains("(") || !line.contains(")") || !line.endsWith("{")) {
            return false;
        }
        String normalized = line.stripLeading();
        return !(normalized.startsWith("if ")
            || normalized.startsWith("if(")
            || normalized.startsWith("for ")
            || normalized.startsWith("for(")
            || normalized.startsWith("while ")
            || normalized.startsWith("while(")
            || normalized.startsWith("switch ")
            || normalized.startsWith("switch(")
            || normalized.startsWith("catch ")
            || normalized.startsWith("catch(")
            || normalized.startsWith("try ")
            || normalized.startsWith("try{")
            || normalized.startsWith("else "));
    }

    private int includeLeadingAnnotations(List<String> lines, int declarationIndex) {
        int index = declarationIndex;
        while (index > 0) {
            String previous = lines.get(index - 1).strip();
            if (previous.startsWith("@") || previous.isBlank()) {
                index--;
                continue;
            }
            break;
        }
        return index;
    }

    private int findBlockEnd(List<String> lines, int startIndex) {
        int depth = 0;
        boolean seenOpenBrace = false;
        for (int index = startIndex; index < lines.size(); index++) {
            String line = stripStringLiterals(stripLineComment(lines.get(index)));
            for (int offset = 0; offset < line.length(); offset++) {
                char ch = line.charAt(offset);
                if (ch == '{') {
                    depth++;
                    seenOpenBrace = true;
                } else if (ch == '}') {
                    depth--;
                    if (seenOpenBrace && depth <= 0) {
                        return index + 1;
                    }
                }
            }
        }
        return Math.min(lines.size(), startIndex + 80);
    }

    private Optional<SymbolRange> deepestSymbolContaining(
        List<SymbolRange> symbols,
        int startLine,
        int endLine,
        SymbolType type
    ) {
        return symbols.stream()
            .filter(symbol -> symbol.type == type)
            .filter(symbol -> symbol.startLine <= startLine && symbol.endLine >= endLine)
            .min((left, right) -> Integer.compare(left.length(), right.length()));
    }

    private SymbolRange fileWindow(List<String> lines, int startLine, int endLine) {
        int start = Math.max(1, startLine - 20);
        int end = Math.min(lines.size(), endLine + 20);
        return new SymbolRange("lines-" + startLine + "-" + endLine, SymbolType.FILE_WINDOW, start, end);
    }

    private String extract(List<String> lines, int startLine, int endLine) {
        StringBuilder builder = new StringBuilder();
        for (int lineNumber = startLine; lineNumber <= endLine && lineNumber <= lines.size(); lineNumber++) {
            builder.append(String.format("%4d | %s%n", lineNumber, lines.get(lineNumber - 1)));
        }
        return builder.toString().stripTrailing();
    }

    private String stripLineComment(String line) {
        int index = line.indexOf("//");
        if (index < 0) {
            return line;
        }
        return line.substring(0, index);
    }

    private String stripStringLiterals(String line) {
        StringBuilder builder = new StringBuilder(line.length());
        boolean inString = false;
        boolean escaped = false;
        for (int index = 0; index < line.length(); index++) {
            char ch = line.charAt(index);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (ch == '\\') {
                    escaped = true;
                } else if (ch == '"') {
                    inString = false;
                }
                builder.append(' ');
            } else {
                if (ch == '"') {
                    inString = true;
                    builder.append(' ');
                } else {
                    builder.append(ch);
                }
            }
        }
        return builder.toString();
    }

    private record SymbolRange(String name, SymbolType type, int startLine, int endLine) {
        int length() {
            return endLine - startLine;
        }
    }
}
