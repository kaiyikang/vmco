package com.reviewm.adapter.out.context.java;

import com.reviewm.application.port.out.CodeContextPort;
import com.reviewm.domain.model.ChangeType;
import com.reviewm.domain.model.ChangedFile;
import com.reviewm.domain.model.CodeContext;
import com.reviewm.domain.model.DiffHunk;
import com.reviewm.domain.model.SymbolType;

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
    public List<CodeContext> collect(ChangedFile file, List<String> lines) {
        List<SymbolRange> symbols = scanSymbols(lines);
        Map<String, CodeContext> contexts = new LinkedHashMap<>();
        for (DiffHunk hunk : file.hunks()) {
            ResolvedContext context = contextForHunk(file, lines, symbols, hunk);
            contexts.putIfAbsent(context.key(), context.value());
        }
        return new ArrayList<>(contexts.values());
    }

    private List<SymbolRange> scanSymbols(List<String> lines) {
        List<SymbolRange> ranges = new ArrayList<>();
        for (int index = 0; index < lines.size(); index++) {
            scanSymbol(lines, index).ifPresent(ranges::add);
        }
        return ranges;
    }

    private ResolvedContext contextForHunk(
        ChangedFile file,
        List<String> lines,
        List<SymbolRange> symbols,
        DiffHunk hunk
    ) {
        int start = Math.max(1, hunk.newStart());
        int end = Math.max(start, hunk.newEndInclusive());
        SymbolRange selected = symbolForRange(lines, symbols, start, end);
        CodeContext context = new CodeContext(
            file.path(),
            selected.name,
            selected.type,
            "Changed lines " + start + "-" + end,
            extract(lines, selected.startLine, selected.endLine)
        );
        return new ResolvedContext(contextKey(selected), context);
    }

    private String contextKey(SymbolRange symbol) {
        return symbol.type + ":" + symbol.name + ":" + symbol.startLine + ":" + symbol.endLine;
    }

    private SymbolRange symbolForRange(List<String> lines, List<SymbolRange> symbols, int startLine, int endLine) {
        Optional<SymbolRange> method = deepestSymbolContaining(symbols, startLine, endLine, SymbolType.METHOD);
        Optional<SymbolRange> type = deepestSymbolContaining(symbols, startLine, endLine, SymbolType.CLASS);
        return method.or(() -> type).orElseGet(() -> fileWindow(lines, startLine, endLine));
    }

    private Optional<SymbolRange> scanSymbol(List<String> lines, int index) {
        String line = stripLineComment(lines.get(index)).strip();
        if (line.isBlank()) {
            return Optional.empty();
        }

        Optional<SymbolRange> type = typeSymbol(lines, index, line);
        if (type.isPresent()) {
            return type;
        }
        return methodSymbol(lines, index, line);
    }

    private Optional<SymbolRange> typeSymbol(List<String> lines, int index, String line) {
        Matcher matcher = TYPE_PATTERN.matcher(line);
        if (!matcher.find()) {
            return Optional.empty();
        }
        return Optional.of(new SymbolRange(
            matcher.group(2),
            SymbolType.CLASS,
            index + 1,
            findBlockEnd(lines, index)
        ));
    }

    private Optional<SymbolRange> methodSymbol(List<String> lines, int index, String line) {
        if (!looksLikeMethodDeclaration(line)) {
            return Optional.empty();
        }
        Matcher matcher = METHOD_NAME_PATTERN.matcher(line);
        if (!matcher.find()) {
            return Optional.empty();
        }
        return Optional.of(new SymbolRange(
            matcher.group(1),
            SymbolType.METHOD,
            includeLeadingAnnotations(lines, index) + 1,
            findBlockEnd(lines, index)
        ));
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
        BlockDepth blockDepth = new BlockDepth();
        for (int index = startIndex; index < lines.size(); index++) {
            String line = stripStringLiterals(stripLineComment(lines.get(index)));
            if (blockDepth.closesBlock(line)) {
                return index + 1;
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
        return new StringLiteralMasker().mask(line);
    }

    private static final class BlockDepth {
        private int depth;
        private boolean seenOpenBrace;

        private boolean closesBlock(String line) {
            for (int offset = 0; offset < line.length(); offset++) {
                if (accept(line.charAt(offset))) {
                    return true;
                }
            }
            return false;
        }

        private boolean accept(char ch) {
            if (ch == '{') {
                depth++;
                seenOpenBrace = true;
                return false;
            }
            if (ch != '}') {
                return false;
            }
            depth--;
            return seenOpenBrace && depth <= 0;
        }
    }

    private static final class StringLiteralMasker {
        private boolean inString;
        private boolean escaped;

        private String mask(String line) {
            StringBuilder builder = new StringBuilder(line.length());
            for (int index = 0; index < line.length(); index++) {
                builder.append(mask(line.charAt(index)));
            }
            return builder.toString();
        }

        private char mask(char ch) {
            if (inString) {
                return maskStringCharacter(ch);
            }
            return maskCodeCharacter(ch);
        }

        private char maskStringCharacter(char ch) {
            if (escaped) {
                escaped = false;
            } else if (ch == '\\') {
                escaped = true;
            } else if (ch == '"') {
                inString = false;
            }
            return ' ';
        }

        private char maskCodeCharacter(char ch) {
            if (ch == '"') {
                inString = true;
                return ' ';
            }
            return ch;
        }
    }

    private record SymbolRange(String name, SymbolType type, int startLine, int endLine) {
        int length() {
            return endLine - startLine;
        }
    }

    private record ResolvedContext(String key, CodeContext value) {
    }
}
