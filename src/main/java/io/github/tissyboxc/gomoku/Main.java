package io.github.tissyboxc.gomoku;

import io.github.tissyboxc.gomoku.database.DatabaseInitializer;
import io.github.tissyboxc.gomoku.server.GomokuServer;
import io.github.tissyboxc.gomoku.ui.GameFrame;
import java.io.IOException;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * 程序入口。不带参数时启动图形界面，带 server 参数时启动服务器。
 *
 * 用法：
 *   java -cp ... io.github.tissyboxc.gomoku.Main
 *   java -cp ... io.github.tissyboxc.gomoku.Main server [端口]
 */
public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        DatabaseInitializer.initialize();
        if (args.length > 0 && "server".equalsIgnoreCase(args[0])) {
            int port = args.length > 1 ? parsePort(args[1]) : GomokuServer.DEFAULT_PORT;
            startServer(port);
            return;
        }
        startGui();
    }

    private static int parsePort(String value) {
        try {
            int port = Integer.parseInt(value);
            return port > 0 && port <= 65535 ? port : GomokuServer.DEFAULT_PORT;
        } catch (NumberFormatException exception) {
            return GomokuServer.DEFAULT_PORT;
        }
    }

    private static void startServer(int port) {
        try {
            GomokuServer server = new GomokuServer(port);
            server.start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    server.close();
                } catch (IOException ignored) {
                    // 退出时关闭失败不再提示。
                }
            }));
        } catch (IOException exception) {
            System.err.println("[服务器] 启动失败：" + exception.getMessage());
        }
    }

    private static void startGui() {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // 系统外观不可用时保留 Swing 默认外观。
            }
            GameFrame frame = new GameFrame();
            frame.setVisible(true);
        });
    }
}
