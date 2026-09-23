package io.github.tissyboxc.gomoku.network;

import io.github.tissyboxc.gomoku.model.Point;
import io.github.tissyboxc.gomoku.model.Stone;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 联机客户端，负责建立 Socket、收发文本协议消息。
 */
public final class OnlineClient implements AutoCloseable {
    private final String host;
    private final int port;
    private final NetworkListener listener;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private Socket socket;
    private BufferedReader reader;
    private BufferedWriter writer;
    private Thread readerThread;

    public OnlineClient(String host, int port, NetworkListener listener) {
        this.host = host;
        this.port = port;
        this.listener = listener;
    }

    /**
     * 连接服务器。登录或注册消息会在这之后由界面发送。
     */
    public void connect() throws IOException {
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), 5000);
        socket.setKeepAlive(true);
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        listener.onConnected(host + ":" + port);

        readerThread = new Thread(this::readLoop, "gomoku-client-reader");
        readerThread.setDaemon(true);
        readerThread.start();
    }

    public void register(String username, String password) {
        send(NetworkMessage.of("REGISTER", username, password));
    }

    public void login(String username, String password) {
        send(NetworkMessage.of("LOGIN", username, password));
    }

    public void requestRandomMatch() {
        send(NetworkMessage.of("MATCH_RANDOM"));
    }

    public void requestTargetMatch(String targetUsername) {
        send(NetworkMessage.of("MATCH_TARGET", targetUsername));
    }

    public void cancelMatch() {
        send(NetworkMessage.of("MATCH_CANCEL"));
    }

    /**
     * 离开当前房间，但保持服务器连接和登录状态。
     */
    public void leaveRoom() {
        send(NetworkMessage.of("LEAVE_ROOM"));
    }

    public void respondMatchInvite(String requester, boolean accepted) {
        send(NetworkMessage.of(
                "MATCH_INVITE_RESPONSE",
                requester,
                accepted ? "YES" : "NO"));
    }

    public void sendMove(int row, int col) {
        send(NetworkMessage.of("MOVE", Integer.toString(row), Integer.toString(col)));
    }

    public void sendChat(String content) {
        send(NetworkMessage.of("CHAT", content));
    }

    public void requestUndo() {
        send(NetworkMessage.of("UNDO_REQUEST"));
    }

    public void respondUndo(boolean accepted) {
        send(NetworkMessage.of("UNDO_RESPONSE", accepted ? "YES" : "NO"));
    }

    public void requestRestart() {
        send(NetworkMessage.of("RESTART_REQUEST"));
    }

    public void respondRestart(boolean accepted) {
        send(NetworkMessage.of("RESTART_RESPONSE", accepted ? "YES" : "NO"));
    }

    private synchronized void send(NetworkMessage message) {
        if (closed.get() || writer == null) {
            return;
        }
        try {
            writer.write(message.encode());
            writer.newLine();
            writer.flush();
        } catch (IOException exception) {
            closeWithMessage("发送消息失败：" + exception.getMessage());
        }
    }

    private void readLoop() {
        try {
            String line;
            while (!closed.get() && (line = reader.readLine()) != null) {
                NetworkMessage message = NetworkMessage.decode(line);
                dispatch(message);
            }
        } catch (ProtocolException exception) {
            listener.onError("收到无法识别的服务器消息");
        } catch (IOException exception) {
            if (!closed.get()) {
                closeWithMessage("与服务器断开连接");
            }
        } finally {
            close();
        }
    }

    private void dispatch(NetworkMessage message) {
        switch (message.type()) {
            case "REGISTER_RESULT" -> listener.onRegisterResult(
                    "OK".equalsIgnoreCase(message.field(0)),
                    message.field(1),
                    message.field(2));
            case "LOGIN_RESULT" -> listener.onLoginResult(
                    "OK".equalsIgnoreCase(message.field(0)),
                    message.field(1),
                    message.field(2));
            case "ONLINE_USERS" -> listener.onOnlineUsers(parseUsers(message.field(0)));
            case "MATCH_WAITING" -> listener.onMatchWaiting(
                    message.intField(0, 60),
                    message.field(1),
                    message.field(2));
            case "MATCH_INVITE" -> listener.onMatchInvite(message.field(0));
            case "MATCH_TIMEOUT" -> listener.onMatchTimeout(message.field(0));
            case "MATCH_CANCELLED" -> listener.onMatchCancelled(message.field(0));
            case "START" -> listener.onStart(
                    message.field(0),
                    message.field(1),
                    parseStone(message.intField(2, 0)));
            case "MOVE" -> listener.onMove(
                    message.intField(0, -1),
                    message.intField(1, -1),
                    parseStone(message.intField(2, 0)),
                    parseStone(message.intField(3, 0)));
            case "CHAT" -> listener.onChat(message.field(0), message.field(1));
            case "UNDO_REQUEST" -> listener.onUndoRequest(message.field(0));
            case "UNDO_RESULT" -> listener.onUndoResult(
                    "YES".equalsIgnoreCase(message.field(0)),
                    message.field(1));
            case "TURN" -> listener.onTurn(parseStone(message.intField(0, 0)));
            case "RESTART_REQUEST" -> listener.onRestartRequest(message.field(0));
            case "GAME_END" -> listener.onGameEnd(
                    parseStone(message.intField(0, 0)),
                    parsePoints(message.field(1)),
                    "DRAW".equalsIgnoreCase(message.field(2)));
            case "PEER_LEFT" -> listener.onPeerLeft();
            case "ERROR" -> listener.onError(message.field(0));
            case "INFO" -> listener.onChat("系统", message.field(0));
            default -> listener.onError("未知消息类型：" + message.type());
        }
    }

    private List<String> parseUsers(String value) {
        List<String> users = new ArrayList<>();
        if (value == null || value.isBlank()) {
            return users;
        }
        for (String name : value.split(",")) {
            if (!name.isBlank()) {
                users.add(name.trim());
            }
        }
        return users;
    }

    private Stone parseStone(int value) {
        return switch (value) {
            case 1 -> Stone.BLACK;
            case 2 -> Stone.WHITE;
            default -> Stone.EMPTY;
        };
    }

    private java.util.List<Point> parsePoints(String value) {
        java.util.List<Point> points = new java.util.ArrayList<>();
        if (value == null || value.isBlank()) {
            return points;
        }
        for (String part : value.split(";")) {
            String[] pair = part.split(",");
            if (pair.length == 2) {
                try {
                    points.add(new Point(Integer.parseInt(pair[0]), Integer.parseInt(pair[1])));
                } catch (NumberFormatException ignored) {
                    // 单个坏坐标不影响其余胜负连线解析。
                }
            }
        }
        return points;
    }

    private void closeWithMessage(String message) {
        if (closed.compareAndSet(false, true)) {
            listener.onConnectionClosed(message);
            closeQuietly();
        }
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            closeQuietly();
        }
    }

    private void closeQuietly() {
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException ignored) {
            // 关闭连接时出现异常不再重复提示。
        }
    }
}
