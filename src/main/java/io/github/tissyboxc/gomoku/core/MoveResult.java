package io.github.tissyboxc.gomoku.core;

import io.github.tissyboxc.gomoku.model.Move;
import io.github.tissyboxc.gomoku.model.Point;
import io.github.tissyboxc.gomoku.model.Stone;
import java.util.List;

/**
 * 落子结果，界面根据该对象更新状态和胜负提示。
 */
public record MoveResult(
        boolean success,
        String message,
        Move move,
        Stone winner,
        List<Point> winningLine,
        boolean finished) {

    public static MoveResult success(Move move, Stone winner, List<Point> winningLine, boolean finished) {
        return new MoveResult(true, "落子成功", move, winner, List.copyOf(winningLine), finished);
    }

    public static MoveResult failure(String message) {
        return new MoveResult(false, message, null, Stone.EMPTY, List.of(), false);
    }
}
