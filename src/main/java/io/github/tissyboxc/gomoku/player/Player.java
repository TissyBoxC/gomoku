package io.github.tissyboxc.gomoku.player;

import io.github.tissyboxc.gomoku.core.GameSession;
import io.github.tissyboxc.gomoku.model.Point;
import io.github.tissyboxc.gomoku.model.Stone;

/**
 * 玩家抽象类，人类和电脑玩家都通过 decideMove 返回落子位置。
 */
public abstract class Player {
    private final String name;
    private final Stone stone;

    protected Player(String name, Stone stone) {
        this.name = name;
        this.stone = stone;
    }

    public abstract Point decideMove(GameSession session);

    public String getName() {
        return name;
    }

    public Stone getStone() {
        return stone;
    }

    public boolean isComputer() {
        return false;
    }
}
