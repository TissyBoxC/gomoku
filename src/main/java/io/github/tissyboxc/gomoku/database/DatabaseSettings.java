package io.github.tissyboxc.gomoku.database;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * 数据库连接参数，优先使用环境变量，其次使用 database.properties。
 */
public final class DatabaseSettings {
    private static final Properties PROPERTIES = loadProperties();

    private DatabaseSettings() {
    }

    public static boolean isEnabled() {
        return Boolean.parseBoolean(value("GOMOKU_DB_ENABLED", "database.enabled", "true"));
    }

    public static String host() {
        return value("GOMOKU_DB_HOST", "database.host", "localhost");
    }

    public static String port() {
        return value("GOMOKU_DB_PORT", "database.port", "3306");
    }

    public static String databaseName() {
        return value("GOMOKU_DB_NAME", "database.name", "gomoku");
    }

    public static String username() {
        return value("GOMOKU_DB_USER", "database.user", "root");
    }

    public static String password() {
        return value("GOMOKU_DB_PASSWORD", "database.password", "root");
    }

    public static String serverUrlWithoutDatabase() {
        return "jdbc:mysql://" + host() + ":" + port()
                + "/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai"
                + "&characterEncoding=UTF-8";
    }

    public static String serverUrl() {
        return "jdbc:mysql://" + host() + ":" + port() + "/" + databaseName()
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai"
                + "&characterEncoding=UTF-8";
    }

    private static String value(String environmentName, String propertyName, String defaultValue) {
        String environmentValue = System.getenv(environmentName);
        if (environmentValue != null && !environmentValue.isBlank()) {
            return environmentValue.trim();
        }
        return PROPERTIES.getProperty(propertyName, defaultValue).trim();
    }

    private static Properties loadProperties() {
        Properties properties = new Properties();
        try (InputStream input = DatabaseSettings.class.getResourceAsStream("/database.properties")) {
            if (input != null) {
                properties.load(input);
            }
        } catch (IOException ignored) {
            // 配置文件不可读时直接使用代码中的默认值。
        }
        return properties;
    }
}
