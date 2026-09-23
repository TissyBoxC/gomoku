package io.github.tissyboxc.gomoku.server;

import io.github.tissyboxc.gomoku.core.GameSession;
import io.github.tissyboxc.gomoku.core.MoveResult;
import io.github.tissyboxc.gomoku.model.ChatMessage;
import io.github.tissyboxc.gomoku.model.GameMode;
import io.github.tissyboxc.gomoku.model.Point;
import io.github.tissyboxc.gomoku.model.Stone;
import io.github.tissyboxc.gomoku.network.NetworkMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/**
 * 一个联机房间。两名玩家和一张棋盘共享该对象。
 */
final class GameRoom {
    private final GomokuServer server;
    private final Object lock = new Object();
    private final GameSession session = new GameSession();
    private final List<ChatMessage> chats = new ArrayList<>();

    private ClientHandler blackClient;
    private ClientHandler whiteClient;
    private ClientHandler undoRequester;
    private ClientHandler restartRequester;
    private boolean running;
    private boolean finishing;

    GameRoom(GomokuServer server) {
        this.server = server;
    }

    void add(ClientHandler client) {
        synchronized (lock) {
            if (blackClient == null) {
                blackClient = client;
                client.setRoom(this);
                return;
            }
            if (whiteClient == null && blackClient != client) {
                whiteClient = client;
                client.setRoom(this);
                startGame();
                return;
            }
            client.send(NetworkMessage.of("ERROR", "房间已经满员"));
        }
    }

    void remove(ClientHandler client) {
        synchronized (lock) {
            if (client != blackClient && client != whiteClient) {
                return;
            }
            ClientHandler peer = client == blackClient ? whiteClient : blackClient;
            if (client == blackClient) {
                blackClient = null;
            } else {
                whiteClient = null;
            }
            client.setRoom(null);
            if (peer != null) {
                peer.setRoom(null);
            }
            running = false;
            undoRequester = null;
            restartRequester = null;
            if (peer != null) {
                peer.send(NetworkMessage.of("PEER_LEFT"));
            }
            if (blackClient == null && whiteClient == null) {
                server.onRoomClosed();
            }
        }
    }

    void handle(ClientHandler client, NetworkMessage message) {
        switch (message.type()) {
            case "MOVE" -> handleMove(client, message.intField(0, -1), message.intField(1, -1));
            case "CHAT" -> handleChat(client, message.field(0));
            case "UNDO_REQUEST" -> handleUndoRequest(client);
            case "UNDO_RESPONSE" -> handleUndoResponse(client, message.field(0));
            case "RESTART_REQUEST" -> handleRestartRequest(client);
            case "RESTART_RESPONSE" -> handleRestartResponse(client, message.field(0));
            default -> client.send(NetworkMessage.of("ERROR", "不支持的消息：" + message.type()));
        }
    }

    private void startGame() {
        session.reset();
        session.setBlackName(blackClient.getPlayerName());
        session.setWhiteName(whiteClient.getPlayerName());
        chats.clear();
        undoRequester = null;
        restartRequester = null;
        running = true;

        blackClient.send(NetworkMessage.of(
                "START",
                session.getBlackName(),
                session.getWhiteName(),
                Integer.toString(Stone.BLACK.getValue())));
        whiteClient.send(NetworkMessage.of(
                "START",
                session.getBlackName(),
                session.getWhiteName(),
                Integer.toString(Stone.WHITE.getValue())));
    }

    private void handleMove(ClientHandler client, int row, int col) {
        synchronized (lock) {
            if (!running || finishing) {
                client.send(NetworkMessage.of("ERROR", "当前没有进行中的对局"));
                return;
            }
            Stone expected = expectedStone(client);
            if (expected == Stone.EMPTY) {
                return;
            }
            MoveResult result = session.placeMove(row, col, expected);
            if (!result.success()) {
                client.send(NetworkMessage.of("ERROR", result.message()));
                return;
            }

            broadcast(NetworkMessage.of(
                    "MOVE",
                    Integer.toString(row),
                    Integer.toString(col),
                    Integer.toString(expected.getValue()),
                    Integer.toString(session.getCurrentStone().getValue())));

            if (result.finished()) {
                finishing = true;
                running = false;
                broadcast(NetworkMessage.of(
                        "GAME_END",
                        Integer.toString(session.getWinner().getValue()),
                        pointsToString(session.getWinningLine()),
                        session.getWinner() == Stone.EMPTY ? "DRAW" : "WIN"));
                List<ChatMessage> savedChats = List.copyOf(chats);
                GameSession savedSession = session;
                new Thread(() -> server.getRepository().saveCompletedGame(
                        savedSession,
                        GameMode.ONLINE,
                        savedSession.getBlackName(),
                        savedSession.getWhiteName(),
                        savedChats), "gomoku-room-save").start();
            }
        }
    }

    private void handleChat(ClientHandler client, String content) {
        String message = content == null ? "" : content.trim();
        if (message.isEmpty()) {
            return;
        }
        if (message.length() > 300) {
            message = message.substring(0, 300);
        }
        ChatMessage chat = new ChatMessage(client.getPlayerName(), message);
        synchronized (lock) {
            chats.add(chat);
            broadcast(NetworkMessage.of("CHAT", chat.sender(), chat.content()));
        }
    }

    private void handleUndoRequest(ClientHandler client) {
        synchronized (lock) {
            if (!running || session.getMoveCount() == 0) {
                client.send(NetworkMessage.of("ERROR", "当前无法悔棋"));
                return;
            }
            ClientHandler peer = peerOf(client);
            if (peer == null) {
                client.send(NetworkMessage.of("ERROR", "对手已离开"));
                return;
            }
            undoRequester = client;
            peer.send(NetworkMessage.of("UNDO_REQUEST", client.getPlayerName()));
        }
    }

    private void handleUndoResponse(ClientHandler client, String answer) {
        synchronized (lock) {
            if (undoRequester == null || peerOf(undoRequester) != client) {
                return;
            }
            ClientHandler requester = undoRequester;
            undoRequester = null;
            if (!"YES".equalsIgnoreCase(answer)) {
                requester.send(NetworkMessage.of("UNDO_RESULT", "NO", "对方拒绝了悔棋请求"));
                client.send(NetworkMessage.of("UNDO_RESULT", "NO", "已拒绝悔棋请求"));
                return;
            }
            if (session.undoLastMove()) {
                broadcast(NetworkMessage.of(
                        "TURN",
                        Integer.toString(session.getCurrentStone().getValue())));
                requester.send(NetworkMessage.of("UNDO_RESULT", "YES", "对方同意悔棋，已回退一步"));
                client.send(NetworkMessage.of("UNDO_RESULT", "YES", "已同意悔棋，已回退一步"));
            } else {
                broadcast(NetworkMessage.of("UNDO_RESULT", "NO", "没有可回退的棋步"));
            }
        }
    }

    private void handleRestartRequest(ClientHandler client) {
        synchronized (lock) {
            if (blackClient == null || whiteClient == null) {
                return;
            }
            restartRequester = client;
            ClientHandler peer = peerOf(client);
            if (peer == null) {
                restartRequester = null;
                client.send(NetworkMessage.of("ERROR", "对手已离开"));
                return;
            }
            peer.send(NetworkMessage.of("RESTART_REQUEST", client.getPlayerName()));
        }
    }

    private void handleRestartResponse(ClientHandler client, String answer) {
        synchronized (lock) {
            if (restartRequester == null || peerOf(restartRequester) != client) {
                return;
            }
            ClientHandler requester = restartRequester;
            restartRequester = null;
            if ("YES".equalsIgnoreCase(answer)) {
                startGame();
            } else {
                requester.send(NetworkMessage.of("INFO", "对方拒绝了重新开始"));
            }
        }
    }

    private Stone expectedStone(ClientHandler client) {
        if (client == blackClient && session.getCurrentStone() == Stone.BLACK) {
            return Stone.BLACK;
        }
        if (client == whiteClient && session.getCurrentStone() == Stone.WHITE) {
            return Stone.WHITE;
        }
        client.send(NetworkMessage.of("ERROR", "还没有轮到你"));
        return Stone.EMPTY;
    }

    private ClientHandler peerOf(ClientHandler client) {
        if (client == blackClient) {
            return whiteClient;
        }
        if (client == whiteClient) {
            return blackClient;
        }
        return null;
    }

    private void broadcast(NetworkMessage message) {
        if (blackClient != null) {
            blackClient.send(message);
        }
        if (whiteClient != null && whiteClient != blackClient) {
            whiteClient.send(message);
        }
    }

    private String pointsToString(List<Point> points) {
        StringJoiner joiner = new StringJoiner(";");
        for (Point point : points) {
            joiner.add(point.row() + "," + point.col());
        }
        return joiner.toString();
    }
}
