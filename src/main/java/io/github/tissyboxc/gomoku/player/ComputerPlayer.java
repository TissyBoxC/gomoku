package io.github.tissyboxc.gomoku.player;

import io.github.tissyboxc.gomoku.ai.GomokuAI;
import io.github.tissyboxc.gomoku.core.GameSession;
import io.github.tissyboxc.gomoku.model.Point;
import io.github.tissyboxc.gomoku.model.Stone;

/**
 * 电脑玩家，继承 Player 并委托评分表 AI 计算落子。
 */
public final class ComputerPlayer extends Player {
    private final GomokuAI ai = new GomokuAI();

    public ComputerPlayer(String name, Stone stone) {
        super(name, stone);
    }

    @Override
    public Point decideMove(GameSession session) {
        return ai.findBestMove(session.getBoard(), getStone());
    }

    @Override
    public boolean isComputer() {
        return true;
    }
}
