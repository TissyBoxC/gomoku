package io.github.tissyboxc.gomoku.player;

import io.github.tissyboxc.gomoku.core.GameSession;
import io.github.tissyboxc.gomoku.model.Point;
import io.github.tissyboxc.gomoku.model.Stone;

/**
 * 人类玩家由鼠标事件决定落子，因此 decideMove 不直接产生坐标。
 */
public final class HumanPlayer extends Player {
    public HumanPlayer(String name, Stone stone) {
        super(name, stone);
    }

    @Override
    public Point decideMove(GameSession session) {
        return null;
    }
}
