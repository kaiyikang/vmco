package com.reviewm.shared;

public final class TextLimiter {
    private TextLimiter() {
    }

    public static String abbreviateMiddle(String value, int maxChars) {
        if (value == null || value.length() <= maxChars) {
            return value;
        }
        if (maxChars < 20) {
            return value.substring(0, Math.max(0, maxChars));
        }
        int side = (maxChars - 20) / 2;
        return value.substring(0, side)
            + "\n... omitted by reviewm ...\n"
            + value.substring(value.length() - side);
    }
}
