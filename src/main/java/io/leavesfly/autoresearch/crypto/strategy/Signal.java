package io.leavesfly.autoresearch.crypto.strategy;

/**
 * Trading signal types.
 * 0=flat, 1=hold, 2=long, 3=short
 */
public enum Signal {
    FLAT(0),
    HOLD(1),
    LONG(2),
    SHORT(3);

    private final int code;

    Signal(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static Signal fromCode(int code) {
        return switch (code) {
            case 0 -> FLAT;
            case 1 -> HOLD;
            case 2 -> LONG;
            case 3 -> SHORT;
            default -> HOLD;
        };
    }
}
