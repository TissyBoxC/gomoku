package io.github.tissyboxc.gomoku.model;

/**
 * 一次落子的记录。
 */
public record Move(int row, int col, Stone stone, long timeMillis) {

    public Move(int row, int col, Stone stone) {
        this(row, col, stone, System.currentTimeMillis());
    }
}
