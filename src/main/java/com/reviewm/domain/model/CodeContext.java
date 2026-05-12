package com.reviewm.domain.model;

public record CodeContext(
    String filePath,
    String symbolName,
    SymbolType symbolType,
    String reason,
    String content
) {
    public CodeContext withContent(String newContent) {
        return new CodeContext(filePath, symbolName, symbolType, reason, newContent);
    }
}
