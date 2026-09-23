package io.github.tissyboxc.gomoku.server;

import io.github.tissyboxc.gomoku.database.GameRepository;
import io.github.tissyboxc.gomoku.network.NetworkMessage;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 五子棋服务器。
 *
 * 服务器负责账号登录、在线用户大厅和 60 秒匹配，匹配成功后再创建房间。
 * 每个客户端连接由独立线程处理，确保某位玩家暂时忙碌时不会阻塞其他玩家。
 */
public final class GomokuServer implements AutoCloseable {
    public static final int DEFAULT_PORT = 9527;
    public static final int MATCH_TIMEOUT_SECONDS = 60;

    private final int port;
    private final GameRepository repository = new GameRepository();
    private final Set<ClientHandler> clients = ConcurrentHashMap.newKeySet();
    private final Map<String, ClientHandler> onlineUsers = new ConcurrentHashMap<>();
    private final Map<ClientHandler, MatchRequest> activeRequests = new ConcurrentHashMap<>();
    private final Map<String, String> friendRequests = new ConcurrentHashMap<>();
    private final Deque<ClientHandler> randomQueue = new ArrayDeque<>();
    private final Object lobbyLock = new Object();
    private final ScheduledExecutorService matchScheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "gomoku-match-timer");
        thread.setDaemon(true);
        return thread;
    });

    private volatile boolean running;
    private ServerSocket serverSocket;

    public GomokuServer(int port) {
        this.port = port;
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        running = true;
        Thread acceptThread = new Thread(this::acceptLoop, "gomoku-server-accept");
        // 该线程必须是非守护线程，否则 main 返回后服务器进程会立即退出。
        acceptThread.setDaemon(false);
        acceptThread.start();
        System.out.println("[服务器] 已启动，端口：" + port);
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                ClientHandler client = new ClientHandler(socket, this);
                clients.add(client);
                client.start();
                System.out.println("[服务器] 新连接：" + socket.getRemoteSocketAddress());
            } catch (IOException exception) {
                if (running) {
                    System.err.println("[服务器] 接受连接失败：" + exception.getMessage());
                }
            }
        }
    }

    /**
     * 登录成功后把用户名登记到大厅。同一个用户名只允许一个在线连接。
     */
    boolean registerOnline(ClientHandler client, String username) {
        synchronized (lobbyLock) {
            ClientHandler old = onlineUsers.get(username);
            if (old != null && old != client) {
                old.send(NetworkMessage.of("ERROR", "该账号已在另一台客户端登录"));
                old.close();
                return false;
            }
            onlineUsers.put(username, client);
        }
        broadcastOnlineUsers();
        return true;
    }

    /**
     * 随机匹配入口。已有等待者时立即配对，否则加入队列并开始 60 秒计时。
     */
    void requestRandomMatch(ClientHandler client) {
        synchronized (lobbyLock) {
            if (client.getRoom() != null || activeRequests.containsKey(client)) {
                client.send(NetworkMessage.of("ERROR", "当前已经在房间或匹配队列中"));
                return;
            }
            while (!randomQueue.isEmpty()) {
                ClientHandler first = randomQueue.removeFirst();
                if (isMatchable(first)) {
                    createMatchedRoom(first, client);
                    return;
                }
            }
            randomQueue.addLast(client);
            activeRequests.put(
                    client,
                    new MatchRequest(MatchType.RANDOM, null, System.currentTimeMillis()));
            sendWaiting(client, "RANDOM", null);
        }
    }

    /**
     * 按用户名发起指定匹配。目标玩家在线时会收到邀请，接受后立即开局。
     */
    void requestTargetMatch(ClientHandler client, String targetName) {
        String target = normalizeUsername(targetName);
        if (target.isEmpty()) {
            client.send(NetworkMessage.of("ERROR", "请输入要匹配的用户名"));
            return;
        }
        if (target.equals(client.getPlayerName())) {
            client.send(NetworkMessage.of("ERROR", "不能和自己匹配"));
            return;
        }
        synchronized (lobbyLock) {
            if (client.getRoom() != null || activeRequests.containsKey(client)) {
                client.send(NetworkMessage.of("ERROR", "当前已经在房间或匹配队列中"));
                return;
            }
            ClientHandler targetClient = onlineUsers.get(target);
            if (targetClient == null || !isMatchable(targetClient)) {
                client.send(NetworkMessage.of("ERROR", "该用户当前不在线或正在对局"));
                return;
            }
            String previousRequester = friendRequests.get(target);
            if (previousRequester != null && previousRequester.equals(client.getPlayerName())) {
                friendRequests.remove(target, client.getPlayerName());
                createMatchedRoom(client, targetClient);
                return;
            }
            friendRequests.put(target, client.getPlayerName());
            activeRequests.put(
                    client,
                    new MatchRequest(MatchType.TARGET, target, System.currentTimeMillis()));
            targetClient.send(NetworkMessage.of("MATCH_INVITE", client.getPlayerName()));
            sendWaiting(client, "TARGET", target);
        }
    }

    /**
     * 处理指定匹配邀请的接受或拒绝。
     */
    void respondMatchInvite(ClientHandler client, String requesterName, boolean accepted) {
        String requester = normalizeUsername(requesterName);
        synchronized (lobbyLock) {
            String expectedRequester = friendRequests.get(client.getPlayerName());
            if (expectedRequester == null || !expectedRequester.equals(requester)) {
                client.send(NetworkMessage.of("ERROR", "这条匹配邀请已经失效"));
                return;
            }
            friendRequests.remove(client.getPlayerName(), requester);
            ClientHandler requesterClient = onlineUsers.get(requester);
            if (!accepted) {
                if (requesterClient != null) {
                    activeRequests.remove(requesterClient);
                    requesterClient.send(NetworkMessage.of("MATCH_CANCELLED", "对方拒绝了你的匹配邀请"));
                }
                return;
            }
            if (!isMatchable(requesterClient)) {
                client.send(NetworkMessage.of("MATCH_CANCELLED", "邀请方已经离线或进入其他对局"));
                return;
            }
            if (!activeRequests.containsKey(requesterClient)) {
                client.send(NetworkMessage.of("MATCH_CANCELLED", "邀请已经超时，请让好友重新邀请"));
                return;
            }
            activeRequests.remove(requesterClient);
            createMatchedRoom(requesterClient, client);
        }
    }

    /**
     * 取消当前匹配并清理相关邀请。
     */
    void cancelMatch(ClientHandler client) {
        synchronized (lobbyLock) {
            MatchRequest request = activeRequests.remove(client);
            randomQueue.remove(client);
            if (request != null && request.type() == MatchType.TARGET) {
                friendRequests.remove(request.targetName(), client.getPlayerName());
            }
            if (request != null) {
                client.send(NetworkMessage.of("MATCH_CANCELLED", "已取消匹配"));
            }
        }
    }

    /**
     * 离开当前房间但保持登录。对手会收到 PEER_LEFT，之后双方仍可重新匹配。
     */
    void leaveRoom(ClientHandler client) {
        GameRoom room = client.getRoom();
        if (room != null) {
            room.remove(client);
        }
    }

    private void createMatchedRoom(ClientHandler first, ClientHandler second) {
        randomQueue.remove(first);
        randomQueue.remove(second);
        activeRequests.remove(first);
        activeRequests.remove(second);
        if (first.getPlayerName() != null) {
            friendRequests.remove(first.getPlayerName());
        }
        if (second.getPlayerName() != null) {
            friendRequests.remove(second.getPlayerName());
        }
        GameRoom room = new GameRoom(this);
        room.add(first);
        room.add(second);
        System.out.println("[大厅] 匹配成功：" + first.getPlayerName() + " 对 " + second.getPlayerName());
    }

    private void sendWaiting(ClientHandler client, String mode, String targetName) {
        matchScheduler.schedule(
                () -> expireMatch(client, mode),
                MATCH_TIMEOUT_SECONDS,
                TimeUnit.SECONDS);
        client.send(NetworkMessage.of(
                "MATCH_WAITING",
                Integer.toString(MATCH_TIMEOUT_SECONDS),
                mode,
                targetName == null ? "" : targetName));
    }

    private void expireMatch(ClientHandler client, String mode) {
        synchronized (lobbyLock) {
            MatchRequest request = activeRequests.remove(client);
            if (request == null) {
                return;
            }
            randomQueue.remove(client);
            if (request.type() == MatchType.TARGET) {
                friendRequests.remove(request.targetName(), client.getPlayerName());
            }
            client.send(NetworkMessage.of(
                    "MATCH_TIMEOUT",
                    "RANDOM".equals(mode) ? "随机匹配等待超过 60 秒，请重试" : "指定匹配等待超过 60 秒，请重试"));
        }
    }

    private boolean isMatchable(ClientHandler client) {
        return client != null
                && !client.isClosed()
                && client.getRoom() == null
                && client.getPlayerName() != null;
    }

    private void broadcastOnlineUsers() {
        List<String> names = new ArrayList<>(onlineUsers.keySet());
        names.sort(String.CASE_INSENSITIVE_ORDER);
        NetworkMessage message = NetworkMessage.of("ONLINE_USERS", String.join(",", names));
        for (ClientHandler client : clients) {
            if (client.getPlayerName() != null) {
                client.send(message);
            }
        }
    }

    void leave(ClientHandler client) {
        clients.remove(client);
        String username = client.getPlayerName();
        synchronized (lobbyLock) {
            if (username != null) {
                onlineUsers.remove(username, client);
                friendRequests.remove(username);
            }
            randomQueue.remove(client);
            activeRequests.remove(client);
            for (Map.Entry<String, String> entry : new ArrayList<>(friendRequests.entrySet())) {
                if (entry.getValue().equals(username)) {
                    friendRequests.remove(entry.getKey());
                }
            }
        }
        GameRoom room = client.getRoom();
        if (room != null) {
            room.remove(client);
        }
        broadcastOnlineUsers();
    }

    GameRepository getRepository() {
        return repository;
    }

    /**
     * 房间关闭后刷新大厅，双方如果仍在线可以立即重新匹配。
     */
    void onRoomClosed() {
        broadcastOnlineUsers();
    }

    @Override
    public void close() throws IOException {
        running = false;
        matchScheduler.shutdownNow();
        if (serverSocket != null) {
            serverSocket.close();
        }
        for (ClientHandler client : clients) {
            client.close();
        }
        clients.clear();
        onlineUsers.clear();
        randomQueue.clear();
        activeRequests.clear();
        friendRequests.clear();
    }

    private String normalizeUsername(String value) {
        return value == null ? "" : value.trim();
    }

    private enum MatchType {
        RANDOM,
        TARGET
    }

    private record MatchRequest(MatchType type, String targetName, long startedAt) {
    }
}
