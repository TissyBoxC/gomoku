package io.github.tissyboxc.gomoku.ai;

import io.github.tissyboxc.gomoku.core.Board;
import io.github.tissyboxc.gomoku.model.Point;
import io.github.tissyboxc.gomoku.model.Stone;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 简单评分表 AI。先找必胜点和必防点，再对棋子附近的空位进行攻防评分。
 */
public final class GomokuAI {
    private static final int[][] DIRECTIONS = {
            {0, 1},
            {1, 0},
            {1, 1},
            {1, -1}
    };

    public Point findBestMove(Board board, Stone aiStone) {
        if (board.isEmpty()) {
            return new Point(Board.SIZE / 2, Board.SIZE / 2);
        }

        Stone opponent = aiStone.opposite();
        List<Point> candidates = collectCandidates(board);
        if (candidates.isEmpty()) {
            return firstEmptyPoint(board);
        }

        Point winningMove = findImmediateMove(board, candidates, aiStone);
        if (winningMove != null) {
            return winningMove;
        }

        Point blockingMove = findImmediateMove(board, candidates, opponent);
        if (blockingMove != null) {
            return blockingMove;
        }

        Point best = candidates.get(0);
        int bestScore = Integer.MIN_VALUE;
        for (Point point : candidates) {
            int attackScore = scoreMove(board, point, aiStone);
            int defenseScore = scoreMove(board, point, opponent);
            int centerScore = 28 - Math.abs(point.row() - Board.SIZE / 2) * 2
                    - Math.abs(point.col() - Board.SIZE / 2) * 2;
            int total = attackScore * 2 + defenseScore * 3 / 2 + centerScore;
            if (total > bestScore) {
                bestScore = total;
                best = point;
            }
        }
        return best;
    }

    private Point firstEmptyPoint(Board board) {
        for (int row = 0; row < Board.SIZE; row++) {
            for (int col = 0; col < Board.SIZE; col++) {
                if (board.get(row, col) == Stone.EMPTY) {
                    return new Point(row, col);
                }
            }
        }
        return null;
    }

    private Point findImmediateMove(Board board, List<Point> candidates, Stone stone) {
        for (Point point : candidates) {
            if (!board.place(point.row(), point.col(), stone)) {
                continue;
            }
            boolean wins = !board.findWinningLine(point.row(), point.col()).isEmpty();
            board.remove(point.row(), point.col());
            if (wins) {
                return point;
            }
        }
        return null;
    }

    /**
     * 只评估已有棋子两格范围内的位置，避免遍历整个棋盘。
     */
    private List<Point> collectCandidates(Board board) {
        boolean[][] marked = new boolean[Board.SIZE][Board.SIZE];
        List<Point> candidates = new ArrayList<>();
        for (int row = 0; row < Board.SIZE; row++) {
            for (int col = 0; col < Board.SIZE; col++) {
                if (board.get(row, col) == Stone.EMPTY) {
                    continue;
                }
                for (int rowOffset = -2; rowOffset <= 2; rowOffset++) {
                    for (int colOffset = -2; colOffset <= 2; colOffset++) {
                        int targetRow = row + rowOffset;
                        int targetCol = col + colOffset;
                        if (!Board.isValid(targetRow, targetCol)
                                || marked[targetRow][targetCol]
                                || board.get(targetRow, targetCol) != Stone.EMPTY) {
                            continue;
                        }
                        marked[targetRow][targetCol] = true;
                        candidates.add(new Point(targetRow, targetCol));
                    }
                }
            }
        }
        candidates.sort(Comparator.comparingInt(this::centerDistance));
        return candidates;
    }

    private int centerDistance(Point point) {
        return Math.abs(point.row() - Board.SIZE / 2) + Math.abs(point.col() - Board.SIZE / 2);
    }

    private int scoreMove(Board board, Point point, Stone stone) {
        if (!board.place(point.row(), point.col(), stone)) {
            return 0;
        }
        int score = 0;
        for (int[] direction : DIRECTIONS) {
            score += scoreDirection(board, point.row(), point.col(), direction[0], direction[1], stone);
        }
        board.remove(point.row(), point.col());
        return score;
    }

    /**
     * 将某方向九个交叉点转换为字符串，再用棋型表评分。
     */
    private int scoreDirection(
            Board board,
            int row,
            int col,
            int rowStep,
            int colStep,
            Stone stone) {
        StringBuilder pattern = new StringBuilder(9);
        for (int offset = -4; offset <= 4; offset++) {
            int targetRow = row + rowStep * offset;
            int targetCol = col + colStep * offset;
            if (!Board.isValid(targetRow, targetCol)) {
                pattern.append('2');
                continue;
            }
            Stone target = board.get(targetRow, targetCol);
            if (target == stone) {
                pattern.append('1');
            } else if (target == Stone.EMPTY) {
                pattern.append('0');
            } else {
                pattern.append('2');
            }
        }
        return scorePattern(pattern.toString());
    }

    private int scorePattern(String pattern) {
        if (pattern.contains("11111")) {
            return 1_000_000;
        }
        if (pattern.contains("011110")) {
            return 100_000;
        }
        if (pattern.contains("011112") || pattern.contains("211110")
                || pattern.contains("11011") || pattern.contains("10111")
                || pattern.contains("11101")) {
            return 12_000;
        }
        if (pattern.contains("01110")) {
            return 8_000;
        }
        if (pattern.contains("010110") || pattern.contains("011010")) {
            return 6_000;
        }
        if (pattern.contains("001110") || pattern.contains("011100")) {
            return 4_000;
        }
        if (pattern.contains("001100")) {
            return 1_000;
        }
        if (pattern.contains("01010")) {
            return 800;
        }
        if (pattern.contains("00100")) {
            return 100;
        }
        return 0;
    }
}
