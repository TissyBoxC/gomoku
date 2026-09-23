package io.github.tissyboxc.gomoku.database;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * 应用启动时执行建库建表脚本。
 *
 * 初始化失败不会阻止单机游戏启动，但会在控制台输出明确的异常信息，
 * 方便学生实验时排查 MySQL 服务、账号或驱动问题。
 */
public final class DatabaseInitializer {
    private static volatile boolean initialized;
    private static volatile boolean available;

    private DatabaseInitializer() {
    }

    public static synchronized void initialize() {
        if (!DatabaseSettings.isEnabled()) {
            System.out.println("[数据库] 已通过配置关闭数据库初始化");
            return;
        }
        if (initialized) {
            return;
        }
        initialized = true;

        try {
            DatabaseManager.loadDriver();
        } catch (SQLException exception) {
            available = false;
            System.err.println("[数据库] 初始化失败：");
            System.err.println("  " + exception.getMessage());
            return;
        }

        try (Connection serverConnection = DriverManager.getConnection(
                DatabaseSettings.serverUrlWithoutDatabase(),
                DatabaseSettings.username(),
                DatabaseSettings.password())) {
            try (Statement serverStatement = serverConnection.createStatement()) {
                serverStatement.execute(
                        "CREATE DATABASE IF NOT EXISTS `" + safeDatabaseName() + "`"
                                + " DEFAULT CHARACTER SET utf8mb4 DEFAULT COLLATE utf8mb4_unicode_ci");
            }

            String script = readInitScript();
            try (Connection connection = DriverManager.getConnection(
                    DatabaseSettings.serverUrl(),
                    DatabaseSettings.username(),
                    DatabaseSettings.password())) {
                for (String sql : splitStatements(script)) {
                    try (Statement statement = connection.createStatement()) {
                        statement.execute(sql);
                    }
                }
            }
            available = true;
            System.out.println("[数据库] 初始化完成：" + DatabaseSettings.databaseName());
        } catch (SQLException | IOException exception) {
            available = false;
            System.err.println("[数据库] 初始化失败，联机对局仍可运行，但记录不会入库：");
            System.err.println("  " + exception.getMessage());
        }
    }

    public static boolean isAvailable() {
        return available;
    }

    private static String safeDatabaseName() {
        String name = DatabaseSettings.databaseName();
        if (!name.matches("[A-Za-z0-9_]+")) {
            throw new IllegalStateException("database.name 只能包含字母、数字和下划线");
        }
        return name;
    }

    private static String readInitScript() throws IOException {
        try (InputStream input = DatabaseInitializer.class.getResourceAsStream("/sql/init.sql")) {
            if (input == null) {
                throw new IOException("找不到资源文件 /sql/init.sql");
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * init.sql 中没有存储过程，因此可以按分号拆分为普通 JDBC 语句。
     */
    private static List<String> splitStatements(String script) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String rawLine : script.split("\\R")) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("--")) {
                continue;
            }
            current.append(rawLine).append('\n');
            if (line.endsWith(";")) {
                String sql = current.toString().trim();
                statements.add(sql.substring(0, sql.length() - 1));
                current.setLength(0);
            }
        }
        if (!current.toString().isBlank()) {
            statements.add(current.toString().trim());
        }
        return statements;
    }
}
