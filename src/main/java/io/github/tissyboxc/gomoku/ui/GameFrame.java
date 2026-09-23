package io.github.tissyboxc.gomoku.ui;

import io.github.tissyboxc.gomoku.core.GameSession;
import io.github.tissyboxc.gomoku.core.MoveResult;
import io.github.tissyboxc.gomoku.database.GameRepository;
import io.github.tissyboxc.gomoku.model.ChatMessage;
import io.github.tissyboxc.gomoku.model.GameMode;
import io.github.tissyboxc.gomoku.model.Point;
import io.github.tissyboxc.gomoku.model.Stone;
import io.github.tissyboxc.gomoku.network.NetworkListener;
import io.github.tissyboxc.gomoku.network.OnlineClient;
import io.github.tissyboxc.gomoku.player.ComputerPlayer;
import io.github.tissyboxc.gomoku.replay.ReplayFileService;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * 五子棋主窗口，负责串接棋盘、聊天、网络、数据库和回放功能。
 */
public final class GameFrame extends JFrame implements NetworkListener {
    private static final int DEFAULT_SERVER_PORT = 9527;

    private final GameSession session = new GameSession();
    private final BoardPanel boardPanel = new BoardPanel(session);
    private final JLabel statusLabel = new JLabel();
    private final JLabel modeLabel = new JLabel();
    private final JTextArea chatArea = new JTextArea();
    private final JTextField chatInput = new JTextField();
    private final JButton undoButton = new JButton("申请悔棋");
    private final JButton restartButton = new JButton("重新开始");
    private final JButton saveReplayButton = new JButton("保存回放");
    private final JButton loadReplayButton = new JButton("打开回放");
    private final JButton themeButton = new JButton("更换背景");
    private final JButton clearButton = new JButton("清屏动画");
    private final ComputerPlayer computerPlayer;
    private final ReplayFileService replayFileService = new ReplayFileService();
    private final GameRepository gameRepository = new GameRepository();
    private final ExecutorService aiExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "gomoku-ai");
        thread.setDaemon(true);
        return thread;
    });
    private final ExecutorService networkExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "gomoku-network-connect");
        thread.setDaemon(true);
        return thread;
    });
    private final List<ChatMessage> onlineChats = new ArrayList<>();
    private final AtomicBoolean networkConnecting = new AtomicBoolean(false);

    private GameMode mode = GameMode.LOCAL_PVP;
    private OnlineClient onlineClient;
    private Stone onlineStone = Stone.EMPTY;
    private boolean networkTurn;
    private boolean waitingForUndoResponse;
    private boolean pendingGameSave;
    private boolean localGamePersisted;
    private boolean loggedIn;
    private String loggedInUser;
    private String matchMode;
    private String matchTarget;
    private javax.swing.Timer matchCountdownTimer;
    private int matchRemainingSeconds;
    private JLabel matchStatusLabel;
    private JLabel onlineUsersLabel;
    private JButton randomMatchButton;
    private JButton targetMatchButton;
    private JButton cancelMatchButton;
    private JButton connectButton;
    private JTextField hostField;
    private JTextField portField;

    public GameFrame() {
        super("网络五子棋 - Java Swing");
        computerPlayer = new ComputerPlayer("电脑", Stone.WHITE);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setLayout(new BorderLayout(12, 12));
        setMinimumSize(new Dimension(1120, 780));
        setSize(1280, 860);
        setLocationRelativeTo(null);

        add(createToolbar(), BorderLayout.NORTH);
        add(boardPanel, BorderLayout.CENTER);
        add(createSidePanel(), BorderLayout.EAST);
        installEvents();
        switchLocalMode(GameMode.LOCAL_PVP);
        setVisible(true);
        SwingUtilities.invokeLater(() -> connectToServer("127.0.0.1", Integer.toString(DEFAULT_SERVER_PORT), false));
    }

    private JPanel createToolbar() {
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        toolbar.setBorder(BorderFactory.createEmptyBorder(2, 6, 0, 6));

        JButton localButton = new JButton("本地双人");
        JButton aiButton = new JButton("人机对战");
        JButton onlineButton = new JButton("联机对战");
        localButton.addActionListener(event -> switchLocalMode(GameMode.LOCAL_PVP));
        aiButton.addActionListener(event -> switchLocalMode(GameMode.LOCAL_AI));
        onlineButton.addActionListener(event -> {
            mode = GameMode.ONLINE;
            boardPanel.setInteractive(false);
            refreshStatus();
            appendMessage("系统", "请在右侧联机大厅连接服务器并登录账号");
        });

        toolbar.add(localButton);
        toolbar.add(aiButton);
        toolbar.add(onlineButton);
        toolbar.add(restartButton);
        toolbar.add(undoButton);
        toolbar.add(themeButton);
        toolbar.add(clearButton);

        modeLabel.setBorder(BorderFactory.createEmptyBorder(0, 20, 0, 0));
        toolbar.add(modeLabel);
        return toolbar;
    }

    /**
     * 创建服务器大厅面板。用户登录后才能发起随机匹配或指定用户名匹配。
     */
    private JPanel createLobbyPanel() {
        JPanel lobbyPanel = new JPanel(new BorderLayout(6, 6));
        lobbyPanel.setBorder(BorderFactory.createTitledBorder("联机大厅"));

        JPanel accountPanel = new JPanel(new GridLayout(3, 2, 6, 6));
        JTextField usernameField = new JTextField();
        JPasswordField passwordField = new JPasswordField();
        JButton registerButton = new JButton("注册");
        JButton loginButton = new JButton("登录");
        accountPanel.add(new JLabel("用户名"));
        accountPanel.add(usernameField);
        accountPanel.add(new JLabel("密码"));
        accountPanel.add(passwordField);
        accountPanel.add(registerButton);
        accountPanel.add(loginButton);

        JPanel connectionPanel = new JPanel(new GridLayout(3, 2, 6, 6));
        hostField = new JTextField("127.0.0.1");
        portField = new JTextField(Integer.toString(DEFAULT_SERVER_PORT));
        connectButton = new JButton("连接服务器");
        connectionPanel.add(new JLabel("服务器"));
        connectionPanel.add(hostField);
        connectionPanel.add(new JLabel("端口"));
        connectionPanel.add(portField);
        connectionPanel.add(new JLabel("联机状态"));
        connectionPanel.add(connectButton);

        JPanel matchPanel = new JPanel(new GridLayout(4, 2, 6, 6));
        JTextField targetField = new JTextField();
        randomMatchButton = new JButton("随机匹配");
        targetMatchButton = new JButton("指定匹配");
        cancelMatchButton = new JButton("取消匹配");
        matchStatusLabel = new JLabel("未登录");
        matchPanel.add(new JLabel("指定用户"));
        matchPanel.add(targetField);
        matchPanel.add(randomMatchButton);
        matchPanel.add(targetMatchButton);
        matchPanel.add(cancelMatchButton);
        matchPanel.add(matchStatusLabel);

        onlineUsersLabel = new JLabel("在线用户：未连接");
        JPanel north = new JPanel(new BorderLayout(4, 4));
        north.add(connectionPanel, BorderLayout.NORTH);
        north.add(accountPanel, BorderLayout.CENTER);
        north.add(onlineUsersLabel, BorderLayout.SOUTH);

        JPanel content = new JPanel(new BorderLayout(6, 6));
        content.add(north, BorderLayout.NORTH);
        content.add(matchPanel, BorderLayout.CENTER);
        lobbyPanel.add(content, BorderLayout.NORTH);

        connectButton.addActionListener(event -> connectToServer(
                hostField.getText().trim(),
                portField.getText().trim(),
                true));
        registerButton.addActionListener(event -> registerAccount(
                usernameField.getText().trim(),
                new String(passwordField.getPassword())));
        loginButton.addActionListener(event -> loginAccount(
                usernameField.getText().trim(),
                new String(passwordField.getPassword())));
        randomMatchButton.addActionListener(event -> requestRandomMatch());
        targetMatchButton.addActionListener(event -> requestTargetMatch(targetField.getText().trim()));
        cancelMatchButton.addActionListener(event -> cancelMatch());
        randomMatchButton.setEnabled(false);
        targetMatchButton.setEnabled(false);
        cancelMatchButton.setEnabled(false);
        return lobbyPanel;
    }

    private JPanel createSidePanel() {
        JPanel side = new JPanel(new BorderLayout(8, 8));
        side.setPreferredSize(new Dimension(360, 0));
        side.setBorder(BorderFactory.createEmptyBorder(6, 4, 10, 10));

        JPanel top = new JPanel(new BorderLayout(8, 8));
        JPanel statusPanel = new JPanel(new GridLayout(0, 1, 4, 4));
        statusPanel.setBorder(BorderFactory.createTitledBorder("对局状态"));
        statusPanel.add(statusLabel);
        top.add(statusPanel, BorderLayout.NORTH);
        top.add(createLobbyPanel(), BorderLayout.CENTER);
        side.add(top, BorderLayout.NORTH);

        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        chatArea.setRows(18);
        JScrollPane chatScroll = new JScrollPane(chatArea);
        chatScroll.setBorder(BorderFactory.createTitledBorder("聊天 / 消息"));

        JPanel chatSendPanel = new JPanel(new BorderLayout(6, 6));
        JButton sendButton = new JButton("发送");
        chatInput.addActionListener(event -> sendChat());
        sendButton.addActionListener(event -> sendChat());
        chatSendPanel.add(chatInput, BorderLayout.CENTER);
        chatSendPanel.add(sendButton, BorderLayout.EAST);

        JPanel chatPanel = new JPanel(new BorderLayout(6, 6));
        chatPanel.add(chatScroll, BorderLayout.CENTER);
        chatPanel.add(chatSendPanel, BorderLayout.SOUTH);
        side.add(chatPanel, BorderLayout.CENTER);

        JPanel replayPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        replayPanel.setBorder(BorderFactory.createTitledBorder("回放"));
        replayPanel.add(saveReplayButton);
        replayPanel.add(loadReplayButton);
        side.add(replayPanel, BorderLayout.SOUTH);
        return side;
    }

    private void installEvents() {
        boardPanel.setMoveListener(this::handleBoardMove);

        restartButton.addActionListener(event -> handleRestart());
        undoButton.addActionListener(event -> handleUndo());
        themeButton.addActionListener(event -> chooseTheme());
        clearButton.addActionListener(event -> clearBoardAnimation());
        saveReplayButton.addActionListener(event -> saveReplay());
        loadReplayButton.addActionListener(event -> openReplay());

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                shutdownAndExit();
            }
        });
    }

    private void switchLocalMode(GameMode newMode) {
        // 切换本地模式时保留服务器连接和登录状态，只退出当前联机房间。
        if (onlineClient != null && mode == GameMode.ONLINE) {
            onlineClient.cancelMatch();
            onlineClient.leaveRoom();
        }
        stopMatchCountdown();
        if (matchStatusLabel != null) {
            if (loggedIn && loggedInUser != null) {
                matchStatusLabel.setText("已登录：" + loggedInUser);
            } else if (onlineClient != null) {
                matchStatusLabel.setText("已连接，请登录");
            } else {
                matchStatusLabel.setText("未连接");
            }
        }
        if (session.isFinished() && !session.getMoves().isEmpty()) {
            persistFinishedGame();
        }
        mode = newMode;
        onlineStone = Stone.EMPTY;
        networkTurn = false;
        waitingForUndoResponse = false;
        pendingGameSave = false;
        localGamePersisted = false;
        session.reset();
        session.setBlackName(newMode == GameMode.LOCAL_AI ? "玩家" : "黑方");
        session.setWhiteName(newMode == GameMode.LOCAL_AI ? "电脑" : "白方");
        boardPanel.setWinningLine(List.of());
        boardPanel.setInteractive(true);
        chatArea.setText("");
        onlineChats.clear();
        clearBoardAnimation();
        refreshStatus();
        updateLobbyButtons();
        appendMessage("系统", "已切换到" + newMode.getDisplayName());
    }

    /**
     * 建立到服务器的 Socket 连接。连接成功后还需要在联机大厅中登录。
     */
    private void connectToServer(String host, String portText, boolean showFailureDialog) {
        if (host.isEmpty()) {
            if (showFailureDialog) {
                showError("服务器地址不能为空");
            }
            return;
        }
        int port;
        try {
            port = Integer.parseInt(portText);
        } catch (NumberFormatException exception) {
            if (showFailureDialog) {
                showError("端口号必须是数字");
            }
            return;
        }
        if (port <= 0 || port > 65535) {
            if (showFailureDialog) {
                showError("端口号必须在 1 到 65535 之间");
            }
            return;
        }
        if (!networkConnecting.compareAndSet(false, true)) {
            return;
        }

        stopMatchCountdown();
        if (onlineClient != null) {
            onlineClient.close();
        }
        resetOnlineState();
        matchStatusLabel.setText("连接中...");
        connectButton.setEnabled(false);
        connectButton.setText("连接中...");
        appendMessage("系统", "正在连接 " + host + ":" + port + "...");
        networkExecutor.submit(() -> {
            OnlineClient client = null;
            try {
                client = new OnlineClient(host, port, this);
                client.connect();
                OnlineClient connected = client;
                SwingUtilities.invokeLater(() -> {
                    onlineClient = connected;
                    networkConnecting.set(false);
                    connectButton.setEnabled(true);
                    connectButton.setText("重连服务器");
                });
            } catch (IOException exception) {
                OnlineClient failed = client;
                SwingUtilities.invokeLater(() -> {
                    if (failed != null) {
                        failed.close();
                    }
                    onlineClient = null;
                    networkConnecting.set(false);
                    connectButton.setEnabled(true);
                    connectButton.setText("连接服务器");
                    matchStatusLabel.setText("连接失败");
                    appendMessage("系统", "无法连接服务器：" + exception.getMessage());
                    if (showFailureDialog) {
                        showError("无法连接服务器：" + exception.getMessage());
                    }
                });
            }
        });
    }

    private void registerAccount(String username, String password) {
        if (onlineClient == null) {
            showError("请先连接服务器");
            return;
        }
        if (username.isEmpty() || password.isEmpty()) {
            showError("用户名和密码不能为空");
            return;
        }
        onlineClient.register(username, password);
    }

    private void loginAccount(String username, String password) {
        if (onlineClient == null) {
            showError("请先连接服务器");
            return;
        }
        if (username.isEmpty() || password.isEmpty()) {
            showError("用户名和密码不能为空");
            return;
        }
        onlineClient.login(username, password);
    }

    private void requestRandomMatch() {
        if (!loggedIn || onlineClient == null) {
            showError("请先登录账号");
            return;
        }
        onlineClient.requestRandomMatch();
    }

    private void requestTargetMatch(String targetUsername) {
        if (!loggedIn || onlineClient == null) {
            showError("请先登录账号");
            return;
        }
        if (targetUsername.isEmpty()) {
            showError("请输入要匹配的用户名");
            return;
        }
        onlineClient.requestTargetMatch(targetUsername);
    }

    private void cancelMatch() {
        if (onlineClient != null) {
            onlineClient.cancelMatch();
        }
    }

    private void resetOnlineState() {
        mode = GameMode.ONLINE;
        loggedIn = false;
        loggedInUser = null;
        matchMode = null;
        matchTarget = null;
        onlineStone = Stone.EMPTY;
        networkTurn = false;
        session.reset();
        session.setBlackName("黑方");
        session.setWhiteName("白方");
        onlineChats.clear();
        chatArea.setText("");
        pendingGameSave = false;
        localGamePersisted = false;
        boardPanel.setWinningLine(List.of());
        boardPanel.setInteractive(true);
        if (matchStatusLabel != null) {
            matchStatusLabel.setText("未登录");
        }
        if (onlineUsersLabel != null) {
            onlineUsersLabel.setText("在线用户：等待登录");
        }
        updateLobbyButtons();
    }

    private void startMatchCountdown(int seconds, String modeName, String targetName) {
        stopMatchCountdown();
        matchMode = modeName;
        matchTarget = targetName;
        matchRemainingSeconds = seconds;
        matchStatusLabel.setText("匹配中：" + matchRemainingSeconds + " 秒");
        updateLobbyButtons();
        matchCountdownTimer = new Timer(1000, event -> {
            matchRemainingSeconds--;
            if (matchRemainingSeconds <= 0) {
                matchRemainingSeconds = 0;
                matchStatusLabel.setText("匹配已超时");
                stopMatchCountdown();
                updateLobbyButtons();
                return;
            }
            matchStatusLabel.setText("匹配中：" + matchRemainingSeconds + " 秒");
        });
        matchCountdownTimer.start();
    }

    private void stopMatchCountdown() {
        if (matchCountdownTimer != null) {
            matchCountdownTimer.stop();
            matchCountdownTimer = null;
        }
        matchMode = null;
        matchTarget = null;
        updateLobbyButtons();
    }

    private void updateLobbyButtons() {
        if (randomMatchButton == null) {
            return;
        }
        boolean matching = matchCountdownTimer != null && matchCountdownTimer.isRunning();
        boolean canMatch = loggedIn && onlineClient != null && mode == GameMode.ONLINE && !matching;
        boolean connecting = networkConnecting.get();
        if (connectButton != null) {
            connectButton.setEnabled(!connecting);
            if (connecting) {
                connectButton.setText("连接中...");
            } else if (onlineClient != null) {
                connectButton.setText("重连服务器");
            } else {
                connectButton.setText("连接服务器");
            }
        }
        randomMatchButton.setEnabled(canMatch);
        targetMatchButton.setEnabled(canMatch);
        cancelMatchButton.setEnabled(matching);
    }

    private void handleBoardMove(Point point) {
        if (session.isFinished()) {
            return;
        }
        if (mode == GameMode.ONLINE) {
            if (onlineClient == null || onlineStone != session.getCurrentStone() || !networkTurn) {
                appendMessage("系统", "还没有轮到你");
                return;
            }
            networkTurn = false;
            onlineClient.sendMove(point.row(), point.col());
            return;
        }

        MoveResult result = session.placeMove(point.row(), point.col(), session.getCurrentStone());
        if (!result.success()) {
            appendMessage("系统", result.message());
            return;
        }
        boardPanel.repaint();
        afterLocalMove(result);
    }

    private void afterLocalMove(MoveResult result) {
        if (result.finished()) {
            finishLocalGame();
            refreshStatus();
            return;
        }
        refreshStatus();
        if (mode == GameMode.LOCAL_AI && session.getCurrentStone() == computerPlayer.getStone()) {
            boardPanel.setInteractive(false);
            aiExecutor.submit(() -> {
                Point point = computerPlayer.decideMove(session);
                SwingUtilities.invokeLater(() -> performAiMove(point));
            });
        }
    }

    private void performAiMove(Point point) {
        if (point == null || mode != GameMode.LOCAL_AI || session.isFinished()) {
            boardPanel.setInteractive(true);
            return;
        }
        MoveResult result = session.placeMove(point.row(), point.col(), computerPlayer.getStone());
        boardPanel.repaint();
        if (result.success() && result.finished()) {
            finishLocalGame();
        } else {
            boardPanel.setInteractive(true);
        }
        refreshStatus();
    }

    private void finishLocalGame() {
        if (localGamePersisted) {
            return;
        }
        localGamePersisted = true;
        appendMessage("系统", resultText());
        boardPanel.setWinningLine(session.getWinningLine());
        new Thread(() -> gameRepository.saveCompletedGame(
                session,
                mode,
                session.getBlackName(),
                session.getWhiteName(),
                List.of()), "gomoku-database-save").start();
        showGameResult();
    }

    private void handleUndo() {
        if (mode == GameMode.ONLINE) {
            if (onlineClient == null || waitingForUndoResponse || session.getMoveCount() == 0) {
                return;
            }
            waitingForUndoResponse = true;
            onlineClient.requestUndo();
            appendMessage("系统", "已发送悔棋请求");
            return;
        }
        if (session.getMoveCount() == 0) {
            appendMessage("系统", "当前没有可以悔掉的棋");
            return;
        }
        if (mode == GameMode.LOCAL_AI) {
            session.undoLastMove();
            if (!session.getMoves().isEmpty()
                    && session.getLastMove() != null
                    && session.getLastMove().stone() == computerPlayer.getStone()) {
                session.undoLastMove();
            }
        } else {
            session.undoLastMove();
        }
        boardPanel.setWinningLine(List.of());
        boardPanel.repaint();
        refreshStatus();
        appendMessage("系统", "已悔棋一步");
    }

    private void handleRestart() {
        if (mode == GameMode.ONLINE) {
            if (onlineClient != null) {
                onlineClient.requestRestart();
                appendMessage("系统", "已发送重新开始请求");
            }
            return;
        }
        if (session.isFinished() && !session.getMoves().isEmpty()) {
            persistFinishedGame();
        }
        session.reset();
        localGamePersisted = false;
        boardPanel.setWinningLine(List.of());
        clearBoardAnimation();
        refreshStatus();
        appendMessage("系统", "已重新开始");
    }

    private void chooseTheme() {
        BoardTheme[] themes = BoardTheme.values();
        BoardTheme selected = (BoardTheme) JOptionPane.showInputDialog(
                this,
                "选择棋盘背景",
                "更换棋盘背景",
                JOptionPane.PLAIN_MESSAGE,
                null,
                themes,
                BoardTheme.WOOD);
        if (selected != null) {
            boardPanel.setTheme(selected);
        }
    }

    private void clearBoardAnimation() {
        if (mode == GameMode.ONLINE) {
            appendMessage("系统", "联机清屏需要双方确认，已发送重新开始请求");
            handleRestart();
            return;
        }
        boardPanel.playClearAnimation();
        Timer timer = new Timer(900, event -> {
            if (!session.getMoves().isEmpty()) {
                session.reset();
                boardPanel.setWinningLine(List.of());
                refreshStatus();
            }
        });
        timer.setRepeats(false);
        timer.start();
    }

    private void saveReplay() {
        if (session.getMoves().isEmpty()) {
            appendMessage("系统", "当前没有可保存的棋局");
            return;
        }
        try {
            Path file = replayFileService.save(
                    session,
                    mode,
                    session.getBlackName(),
                    session.getWhiteName());
            appendMessage("系统", "回放已保存：" + file.getFileName());
        } catch (IOException exception) {
            showError("保存回放失败：" + exception.getMessage());
        }
    }

    private void openReplay() {
        JFileChooser chooser = new JFileChooser(replayFileService.replayDirectory().toFile());
        chooser.setFileFilter(new FileNameExtensionFilter("五子棋回放 (*.gomoku)", "gomoku"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            ReplayFileService.ReplayData data = replayFileService.load(chooser.getSelectedFile().toPath());
            new ReplayDialog(this, data).setVisible(true);
        } catch (IOException exception) {
            showError("读取回放失败：" + exception.getMessage());
        }
    }

    private void sendChat() {
        String text = chatInput.getText().trim();
        if (text.isEmpty()) {
            return;
        }
        if (mode == GameMode.ONLINE && onlineClient != null) {
            onlineClient.sendChat(text);
        } else {
            appendMessage("我", text);
        }
        chatInput.setText("");
    }

    private void persistFinishedGame() {
        if (pendingGameSave) {
            return;
        }
        pendingGameSave = true;
        List<ChatMessage> chats = List.copyOf(onlineChats);
        new Thread(() -> gameRepository.saveCompletedGame(
                session,
                mode,
                session.getBlackName(),
                session.getWhiteName(),
                chats), "gomoku-database-save").start();
    }

    private void refreshStatus() {
        modeLabel.setText("当前：" + mode.getDisplayName());
        if (session.isFinished()) {
            statusLabel.setText(resultText());
            return;
        }
        String turnName = session.getStoneName(session.getCurrentStone());
        statusLabel.setText("<html>轮到：<b>" + turnName + "</b><br>已落子："
                + session.getMoveCount() + " 手</html>");
    }

    private String resultText() {
        if (!session.isFinished()) {
            return "对局进行中";
        }
        if (session.getWinner() == Stone.EMPTY) {
            return "棋盘已满，本局平局";
        }
        return session.getStoneName(session.getWinner()) + "获胜";
    }

    private void showGameResult() {
        JOptionPane.showMessageDialog(this, resultText(), "对局结束", JOptionPane.INFORMATION_MESSAGE);
    }

    private void appendMessage(String sender, String content) {
        String line;
        if (sender == null || sender.isBlank()) {
            line = content;
        } else {
            line = sender + "：" + content;
        }
        if (!chatArea.getText().isEmpty()) {
            chatArea.append("\n");
        }
        chatArea.append(line);
        chatArea.setCaretPosition(chatArea.getDocument().getLength());
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "提示", JOptionPane.WARNING_MESSAGE);
    }

    @Override
    public void onConnected(String localName) {
        SwingUtilities.invokeLater(() -> {
            mode = GameMode.ONLINE;
            networkConnecting.set(false);
            if (connectButton != null) {
                connectButton.setEnabled(true);
                connectButton.setText("重连服务器");
            }
            matchStatusLabel.setText("已连接，请登录");
            onlineUsersLabel.setText("在线用户：等待登录");
            appendMessage("系统", "已连接服务器，请注册或登录");
        });
    }

    @Override
    public void onRegisterResult(boolean success, String message, String username) {
        SwingUtilities.invokeLater(() -> {
            appendMessage("系统", message);
            if (success) {
                matchStatusLabel.setText("注册成功，请登录");
            }
        });
    }

    @Override
    public void onLoginResult(boolean success, String message, String username) {
        SwingUtilities.invokeLater(() -> {
            if (success) {
                loggedIn = true;
                loggedInUser = username;
                matchStatusLabel.setText("已登录：" + username);
            } else {
                loggedIn = false;
                loggedInUser = null;
                matchStatusLabel.setText("登录失败");
            }
            appendMessage("系统", message);
            updateLobbyButtons();
        });
    }

    @Override
    public void onOnlineUsers(List<String> usernames) {
        SwingUtilities.invokeLater(() -> {
            if (usernames == null || usernames.isEmpty()) {
                onlineUsersLabel.setText("在线用户：无");
            } else {
                onlineUsersLabel.setText("在线用户：" + String.join("、", usernames));
            }
        });
    }

    @Override
    public void onMatchWaiting(int remainingSeconds, String modeName, String targetUsername) {
        SwingUtilities.invokeLater(() -> {
            startMatchCountdown(remainingSeconds, modeName, targetUsername);
            if ("TARGET".equalsIgnoreCase(modeName)) {
                appendMessage("系统", "已向 " + targetUsername + " 发送匹配邀请，等待 60 秒");
            } else {
                appendMessage("系统", "已开始随机匹配，等待 60 秒");
            }
        });
    }

    @Override
    public void onMatchInvite(String requester) {
        SwingUtilities.invokeLater(() -> {
            int answer = JOptionPane.showConfirmDialog(
                    this,
                    requester + " 邀请你进行联机对战，是否接受？",
                    "匹配邀请",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE);
            if (onlineClient != null) {
                onlineClient.respondMatchInvite(requester, answer == JOptionPane.YES_OPTION);
            }
        });
    }

    @Override
    public void onMatchTimeout(String message) {
        SwingUtilities.invokeLater(() -> {
            stopMatchCountdown();
            matchStatusLabel.setText("匹配超时");
            appendMessage("系统", message);
        });
    }

    @Override
    public void onMatchCancelled(String message) {
        SwingUtilities.invokeLater(() -> {
            stopMatchCountdown();
            matchStatusLabel.setText("未匹配");
            appendMessage("系统", message);
        });
    }

    @Override
    public void onStart(String blackName, String whiteName, Stone myStone) {
        SwingUtilities.invokeLater(() -> {
            mode = GameMode.ONLINE;
            session.reset();
            stopMatchCountdown();
            matchStatusLabel.setText("对局中");
            session.setBlackName(blackName);
            session.setWhiteName(whiteName);
            onlineStone = myStone;
            networkTurn = myStone == Stone.BLACK;
            waitingForUndoResponse = false;
            pendingGameSave = false;
            localGamePersisted = false;
            boardPanel.setWinningLine(List.of());
            boardPanel.setInteractive(true);
            boardPanel.playClearAnimation();
            refreshStatus();
            appendMessage("系统", "对局开始，你是" + myStone.getDisplayName()
                    + "（" + session.getStoneName(myStone) + "）");
        });
    }

    @Override
    public void onMove(int row, int col, Stone stone, Stone nextStone) {
        SwingUtilities.invokeLater(() -> {
            MoveResult result = session.placeMove(row, col, stone);
            if (!result.success()) {
                appendMessage("系统", "落子同步失败：" + result.message());
                return;
            }
            networkTurn = !session.isFinished() && nextStone == onlineStone;
            refreshStatus();
            boardPanel.repaint();
        });
    }

    @Override
    public void onChat(String sender, String content) {
        SwingUtilities.invokeLater(() -> {
            onlineChats.add(new ChatMessage(sender, content));
            appendMessage(sender, content);
        });
    }

    @Override
    public void onUndoRequest(String requester) {
        SwingUtilities.invokeLater(() -> {
            int answer = JOptionPane.showConfirmDialog(
                    this,
                    requester + " 请求悔棋，是否同意？",
                    "悔棋申请",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE);
            if (onlineClient != null) {
                onlineClient.respondUndo(answer == JOptionPane.YES_OPTION);
            }
        });
    }

    @Override
    public void onUndoResult(boolean accepted, String message) {
        SwingUtilities.invokeLater(() -> {
            waitingForUndoResponse = false;
            if (accepted) {
                session.undoLastMove();
                boardPanel.setWinningLine(List.of());
                boardPanel.repaint();
                refreshStatus();
            }
            appendMessage("系统", message);
        });
    }

    @Override
    public void onTurn(Stone currentStone) {
        SwingUtilities.invokeLater(() -> {
            networkTurn = currentStone == onlineStone;
            refreshStatus();
        });
    }

    @Override
    public void onRestartRequest(String requester) {
        SwingUtilities.invokeLater(() -> {
            int answer = JOptionPane.showConfirmDialog(
                    this,
                    requester + " 请求重新开始，是否同意？",
                    "重新开始",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE);
            if (onlineClient != null) {
                onlineClient.respondRestart(answer == JOptionPane.YES_OPTION);
            }
        });
    }

    @Override
    public void onGameEnd(Stone winner, List<Point> winningLine, boolean draw) {
        SwingUtilities.invokeLater(() -> {
            boardPanel.setWinningLine(winningLine);
            networkTurn = false;
            refreshStatus();
            appendMessage("系统", draw ? "棋盘已满，本局平局"
                    : session.getStoneName(winner) + "获胜");
            showGameResult();
        });
    }

    @Override
    public void onPeerLeft() {
        SwingUtilities.invokeLater(() -> {
            networkTurn = false;
            onlineStone = Stone.EMPTY;
            matchStatusLabel.setText(loggedIn ? "已登录：" + loggedInUser : "未登录");
            updateLobbyButtons();
            appendMessage("系统", "对手已离开，本局结束");
        });
    }

    @Override
    public void onError(String message) {
        SwingUtilities.invokeLater(() -> appendMessage("系统", message));
    }

    @Override
    public void onConnectionClosed(String message) {
        SwingUtilities.invokeLater(() -> {
            stopMatchCountdown();
            loggedIn = false;
            loggedInUser = null;
            onlineClient = null;
            networkConnecting.set(false);
            if (connectButton != null) {
                connectButton.setEnabled(true);
                connectButton.setText("连接服务器");
            }
            matchStatusLabel.setText("连接已断开");
            networkTurn = false;
            appendMessage("系统", message);
            updateLobbyButtons();
        });
    }

    private void shutdownAndExit() {
        stopMatchCountdown();
        if (onlineClient != null) {
            onlineClient.close();
        }
        aiExecutor.shutdownNow();
        networkExecutor.shutdownNow();
        dispose();
        System.exit(0);
    }
}
