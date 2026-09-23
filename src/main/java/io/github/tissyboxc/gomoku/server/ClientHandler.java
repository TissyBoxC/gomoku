package io.github.tissyboxc.gomoku.server;

import io.github.tissyboxc.gomoku.database.AccountRepository;
import io.github.tissyboxc.gomoku.network.NetworkMessage;
import io.github.tissyboxc.gomoku.network.ProtocolException;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 服务器端客户端连接对象，每个客户端由独立线程处理。
 */
final class ClientHandler implements AutoCloseable {
    private static final int MAX_USERNAME_LENGTH = 20;
    private static final int MAX_PASSWORD_LENGTH = 64;

    private final Socket socket;
    private final GomokuServer server;
    private final AccountRepository accountRepository = new AccountRepository();
    private final BufferedReader reader;
    private final BufferedWriter writer;
    private final Object sendLock = new Object();
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private volatile String playerName;
    private volatile GameRoom room;
    private Thread thread;

    ClientHandler(Socket socket, GomokuServer server) throws IOException {
        this.socket = socket;
        this.server = server;
        this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
    }

    void start() {
        thread = new Thread(this::readLoop, "gomoku-client-handler");
        thread.setDaemon(true);
        thread.start();
    }

    private void readLoop() {
        try {
            String line;
            while (!closed.get() && (line = reader.readLine()) != null) {
                NetworkMessage message = NetworkMessage.decode(line);
                if ("REGISTER".equals(message.type())) {
                    handleRegister(message);
                    continue;
                }
                if ("LOGIN".equals(message.type())) {
                    handleLogin(message);
                    continue;
                }
                if ("MATCH_RANDOM".equals(message.type())) {
                    requireLoginThen(message, () -> server.requestRandomMatch(this));
                    continue;
                }
                if ("MATCH_TARGET".equals(message.type())) {
                    requireLoginThen(message, () -> server.requestTargetMatch(this, message.field(0)));
                    continue;
                }
                if ("MATCH_CANCEL".equals(message.type())) {
                    requireLoginThen(message, () -> server.cancelMatch(this));
                    continue;
                }
                if ("MATCH_INVITE_RESPONSE".equals(message.type())) {
                    requireLoginThen(message, () -> server.respondMatchInvite(
                            this,
                            message.field(0),
                            "YES".equalsIgnoreCase(message.field(1))));
                    continue;
                }
                if ("LEAVE_ROOM".equals(message.type())) {
                    requireLoginThen(message, () -> server.leaveRoom(this));
                    continue;
                }
                if (room == null) {
                    send(NetworkMessage.of("ERROR", "请先登录并匹配对手"));
                    continue;
                }
                room.handle(this, message);
            }
        } catch (ProtocolException exception) {
            send(NetworkMessage.of("ERROR", "消息格式错误"));
        } catch (IOException ignored) {
            // 连接断开由 finally 统一通知房间。
        } finally {
            server.leave(this);
            close();
        }
    }

    private void handleRegister(NetworkMessage message) {
        String username = normalizeUsername(message.field(0));
        String password = message.field(1);
        String validation = validateCredentials(username, password);
        if (validation != null) {
            send(NetworkMessage.of("REGISTER_RESULT", "FAIL", validation, username));
            return;
        }
        String error = accountRepository.register(username, password);
        if (error == null) {
            send(NetworkMessage.of("REGISTER_RESULT", "OK", "注册成功，请登录", username));
        } else {
            send(NetworkMessage.of("REGISTER_RESULT", "FAIL", error, username));
        }
    }

    private void handleLogin(NetworkMessage message) {
        String username = normalizeUsername(message.field(0));
        String password = message.field(1);
        String validation = validateCredentials(username, password);
        if (validation != null) {
            send(NetworkMessage.of("LOGIN_RESULT", "FAIL", validation, username));
            return;
        }
        if (playerName != null && !playerName.equals(username)) {
            send(NetworkMessage.of("LOGIN_RESULT", "FAIL", "当前连接已经登录", username));
            return;
        }
        String error = accountRepository.login(username, password);
        if (error != null) {
            send(NetworkMessage.of("LOGIN_RESULT", "FAIL", error, username));
            return;
        }
        String previousName = playerName;
        playerName = username;
        if (!server.registerOnline(this, username)) {
            playerName = previousName;
            send(NetworkMessage.of("LOGIN_RESULT", "FAIL", "该账号已经在线", username));
            return;
        }
        send(NetworkMessage.of("LOGIN_RESULT", "OK", "登录成功", username));
    }

    private void requireLoginThen(NetworkMessage message, Runnable action) {
        if (playerName == null) {
            send(NetworkMessage.of("ERROR", "请先登录账号"));
            return;
        }
        action.run();
    }

    private String validateCredentials(String username, String password) {
        if (username.isEmpty() || password == null || password.isEmpty()) {
            return "用户名和密码不能为空";
        }
        if (username.length() > MAX_USERNAME_LENGTH) {
            return "用户名不能超过 " + MAX_USERNAME_LENGTH + " 个字符";
        }
        if (!username.matches("[A-Za-z0-9_\\u4e00-\\u9fa5]+")) {
            return "用户名只能包含中文、字母、数字和下划线";
        }
        if (password.length() > MAX_PASSWORD_LENGTH) {
            return "密码不能超过 " + MAX_PASSWORD_LENGTH + " 个字符";
        }
        return null;
    }

    void setRoom(GameRoom room) {
        this.room = room;
    }

    GameRoom getRoom() {
        return room;
    }

    String getPlayerName() {
        return playerName;
    }

    boolean isClosed() {
        return closed.get();
    }

    /**
     * 每个客户端的所有发送操作都经过 sendLock，避免两个房间线程同时写流。
     */
    void send(NetworkMessage message) {
        synchronized (sendLock) {
            if (closed.get()) {
                return;
            }
            try {
                writer.write(message.encode());
                writer.newLine();
                writer.flush();
            } catch (IOException exception) {
                close();
            }
        }
    }

    private String normalizeUsername(String value) {
        return value == null ? "" : value.trim();
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // 连接已经失效，无需重复处理。
            }
        }
    }
}
