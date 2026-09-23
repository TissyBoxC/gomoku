package io.github.tissyboxc.gomoku.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * JDBC 连接工具类。每次操作创建短连接，适合课程实验的简单场景。
 */
public final class DatabaseManager {
    private static final String MYSQL_DRIVER = "com.mysql.cj.jdbc.Driver";
    private static volatile boolean driverLoaded;

    private DatabaseManager() {
    }

    /**
     * 显式加载 MySQL 驱动。
     *
     * 普通 IDEA 运行时 JDBC 4 通常能自动发现驱动；打包为 EXE 或使用
     * 精简运行时后，显式加载更可靠，也能把驱动缺失显示成清晰错误。
     */
    public static synchronized void loadDriver() throws SQLException {
        if (driverLoaded) {
            return;
        }
        try {
            Class.forName(MYSQL_DRIVER);
            driverLoaded = true;
        } catch (ClassNotFoundException exception) {
            throw new SQLException(
                    "找不到 MySQL JDBC 驱动 " + MYSQL_DRIVER
                            + "，请确认打包时已包含 mysql-connector-j",
                    exception);
        }
    }

    public static Connection getConnection() throws SQLException {
        loadDriver();
        return DriverManager.getConnection(
                DatabaseSettings.serverUrl(),
                DatabaseSettings.username(),
                DatabaseSettings.password());
    }
}
