package io.github.tissyboxc.gomoku.network;

import io.github.tissyboxc.gomoku.model.Point;
import io.github.tissyboxc.gomoku.model.Stone;
import java.util.List;

/**
 * 网络客户端事件监听器。图形界面实现该接口后，不需要把 Socket 细节写进 Swing 代码。
 */
public interface NetworkListener {
    default void onConnected(String localName) {
    }

    default void onRegisterResult(boolean success, String message, String username) {
    }

    default void onLoginResult(boolean success, String message, String username) {
    }

    default void onOnlineUsers(List<String> usernames) {
    }

    default void onMatchWaiting(int remainingSeconds, String mode, String targetUsername) {
    }

    default void onMatchInvite(String requester) {
    }

    default void onMatchTimeout(String message) {
    }

    default void onMatchCancelled(String message) {
    }

    default void onStart(String blackName, String whiteName, Stone myStone) {
    }

    default void onMove(int row, int col, Stone stone, Stone nextStone) {
    }

    default void onChat(String sender, String content) {
    }

    default void onUndoRequest(String requester) {
    }

    default void onUndoResult(boolean accepted, String message) {
    }

    default void onTurn(Stone currentStone) {
    }

    default void onRestartRequest(String requester) {
    }

    default void onGameEnd(Stone winner, List<Point> winningLine, boolean draw) {
    }

    default void onPeerLeft() {
    }

    default void onError(String message) {
    }

    default void onConnectionClosed(String message) {
    }
}
