package io.github.tissyboxc.gomoku.core;

import io.github.tissyboxc.gomoku.model.Move;
import io.github.tissyboxc.gomoku.model.Point;
import io.github.tissyboxc.gomoku.model.Stone;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * 一局棋的完整状态，包含棋盘、轮次、历史落子、悔棋和胜负信息。
 */
public final class GameSession {
    private final Board board = new Board();
    private final LinkedList<Move> moves = new LinkedList<>();

    private Stone currentStone = Stone.BLACK;
    private Stone winner = Stone.EMPTY;
    private List<Point> winningLine = List.of();
    private boolean finished;
    private String blackName = "黑方";
    private String whiteName = "白方";

    public synchronized MoveResult placeMove(int row, int col, Stone stone) {
        if (finished) {
            return MoveResult.failure("当前对局已经结束");
        }
        if (stone != currentStone) {
            return MoveResult.failure("还没有轮到" + stone.getDisplayName());
        }
        if (!board.place(row, col, stone)) {
            return MoveResult.failure("该位置不能落子");
        }

        Move move = new Move(row, col, stone);
        moves.addLast(move);
        List<Point> line = board.findWinningLine(row, col);
        if (!line.isEmpty()) {
            finished = true;
            winner = stone;
            winningLine = line;
            return MoveResult.success(move, winner, winningLine, true);
        }
        if (board.isFull()) {
            finished = true;
            winner = Stone.EMPTY;
            return MoveResult.success(move, Stone.EMPTY, List.of(), true);
        }

        currentStone = stone.opposite();
        return MoveResult.success(move, Stone.EMPTY, List.of(), false);
    }

    /**
     * 悔棋一步。回退后由被撤销落子的一方重新落子。
     */
    public synchronized boolean undoLastMove() {
        if (moves.isEmpty()) {
            return false;
        }
        Move move = moves.removeLast();
        board.remove(move.row(), move.col());
        currentStone = move.stone();
        winner = Stone.EMPTY;
        winningLine = List.of();
        finished = false;
        return true;
    }

    public synchronized void reset() {
        board.clear();
        moves.clear();
        currentStone = Stone.BLACK;
        winner = Stone.EMPTY;
        winningLine = List.of();
        finished = false;
    }

    /**
     * 回放时直接载入前若干步，不执行“轮到谁”的限制。
     */
    public synchronized void loadMoves(List<Move> sourceMoves) {
        reset();
        List<Move> safeMoves = sourceMoves == null ? List.of() : List.copyOf(sourceMoves);
        for (Move move : safeMoves) {
            if (!board.place(move.row(), move.col(), move.stone())) {
                continue;
            }
            moves.addLast(move);
            List<Point> line = board.findWinningLine(move.row(), move.col());
            if (!line.isEmpty()) {
                finished = true;
                winner = move.stone();
                winningLine = line;
                break;
            }
            currentStone = move.stone().opposite();
        }
        if (!moves.isEmpty() && !finished) {
            currentStone = moves.getLast().stone().opposite();
        }
    }

    public synchronized Board getBoard() {
        return board;
    }

    public synchronized Stone getCurrentStone() {
        return currentStone;
    }

    public synchronized Stone getWinner() {
        return winner;
    }

    public synchronized List<Point> getWinningLine() {
        return List.copyOf(winningLine);
    }

    public synchronized boolean isFinished() {
        return finished;
    }

    public synchronized int getMoveCount() {
        return moves.size();
    }

    public synchronized Move getLastMove() {
        return moves.isEmpty() ? null : moves.getLast();
    }

    public synchronized List<Move> getMoves() {
        return new ArrayList<>(moves);
    }

    public synchronized String getBlackName() {
        return blackName;
    }

    public synchronized void setBlackName(String blackName) {
        this.blackName = blackName == null || blackName.isBlank() ? "黑方" : blackName.trim();
    }

    public synchronized String getWhiteName() {
        return whiteName;
    }

    public synchronized void setWhiteName(String whiteName) {
        this.whiteName = whiteName == null || whiteName.isBlank() ? "白方" : whiteName.trim();
    }

    public synchronized String getStoneName(Stone stone) {
        if (stone == Stone.BLACK) {
            return blackName;
        }
        if (stone == Stone.WHITE) {
            return whiteName;
        }
        return "平局";
    }
}
