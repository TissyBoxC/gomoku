package io.github.tissyboxc.gomoku.model;

/**
 * 棋盘中的棋子类型。
 */
public enum Stone {
    EMPTY(0, "空位"),
    BLACK(1, "黑棋"),
    WHITE(2, "白棋");

    private final int value;
    private final String displayName;

    Stone(int value, String displayName) {
        this.value = value;
        this.displayName = displayName;
    }

    public int getValue() {
        return value;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * 获取相反颜色的棋子。
     */
    public Stone opposite() {
        if (this == BLACK) {
            return WHITE;
        }
        if (this == WHITE) {
            return BLACK;
        }
        return EMPTY;
    }

    public static Stone fromValue(int value) {
        for (Stone stone : values()) {
            if (stone.value == value) {
                return stone;
            }
        }
        return EMPTY;
    }
}
