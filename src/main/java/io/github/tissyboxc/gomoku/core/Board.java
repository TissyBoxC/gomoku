package io.github.tissyboxc.gomoku.core;

import io.github.tissyboxc.gomoku.model.Point;
import io.github.tissyboxc.gomoku.model.Stone;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * 15 乘 15 的五子棋棋盘及五连判断算法。
 */
public final class Board {
    public static final int SIZE = 15;

    private static final int[][] DIRECTIONS = {
            {0, 1},
            {1, 0},
            {1, 1},
            {1, -1}
    };

    private final Stone[][] grid = new Stone[SIZE][SIZE];

    public Board() {
        clear();
    }

    public synchronized Stone get(int row, int col) {
        if (!isValid(row, col)) {
            return Stone.EMPTY;
        }
        return grid[row][col];
    }

    public static boolean isValid(int row, int col) {
        return row >= 0 && row < SIZE && col >= 0 && col < SIZE;
    }

    public synchronized boolean place(int row, int col, Stone stone) {
        if (!isValid(row, col) || stone == Stone.EMPTY || grid[row][col] != Stone.EMPTY) {
            return false;
        }
        grid[row][col] = stone;
        return true;
    }

    public synchronized void remove(int row, int col) {
        if (isValid(row, col)) {
            grid[row][col] = Stone.EMPTY;
        }
    }

    public synchronized void clear() {
        for (Stone[] row : grid) {
            Arrays.fill(row, Stone.EMPTY);
        }
    }

    public synchronized boolean isEmpty() {
        for (Stone[] row : grid) {
            for (Stone stone : row) {
                if (stone != Stone.EMPTY) {
                    return false;
                }
            }
        }
        return true;
    }

    public synchronized boolean isFull() {
        for (Stone[] row : grid) {
            for (Stone stone : row) {
                if (stone == Stone.EMPTY) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * 判断指定棋子是否形成五连，并返回用于界面高亮的连线。
     */
    public synchronized List<Point> findWinningLine(int row, int col) {
        Stone target = get(row, col);
        if (target == Stone.EMPTY) {
            return List.of();
        }

        for (int[] direction : DIRECTIONS) {
            LinkedList<Point> line = new LinkedList<>();
            collectDirection(line, row, col, direction[0], direction[1], target, false);
            line.add(new Point(row, col));
            collectDirection(line, row, col, direction[0], direction[1], target, true);
            if (line.size() >= 5) {
                return List.copyOf(line);
            }
        }
        return List.of();
    }

    private void collectDirection(
            LinkedList<Point> line,
            int row,
            int col,
            int rowStep,
            int colStep,
            Stone target,
            boolean forward) {
        int currentRow = row;
        int currentCol = col;
        LinkedList<Point> buffer = new LinkedList<>();
        while (true) {
            currentRow += forward ? rowStep : -rowStep;
            currentCol += forward ? colStep : -colStep;
            if (!isValid(currentRow, currentCol) || get(currentRow, currentCol) != target) {
                break;
            }
            Point point = new Point(currentRow, currentCol);
            if (forward) {
                line.add(point);
            } else {
                buffer.addFirst(point);
            }
        }
        if (!buffer.isEmpty()) {
            line.addAll(0, buffer);
        }
    }

    public synchronized Stone[][] snapshot() {
        Stone[][] copy = new Stone[SIZE][SIZE];
        for (int row = 0; row < SIZE; row++) {
            System.arraycopy(grid[row], 0, copy[row], 0, SIZE);
        }
        return copy;
    }
}
