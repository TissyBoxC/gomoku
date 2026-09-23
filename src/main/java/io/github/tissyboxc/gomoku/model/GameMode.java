package io.github.tissyboxc.gomoku.model;

/**
 * 对局模式，数据库和对局记录均保存该名称。
 */
public enum GameMode {
    LOCAL_PVP("本地双人"),
    LOCAL_AI("人机对战"),
    ONLINE("联机对战");

    private final String displayName;

    GameMode(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
