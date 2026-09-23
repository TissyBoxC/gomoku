package io.github.tissyboxc.gomoku.database;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Base64;

/**
 * 玩家账号数据访问类，负责注册、登录和最近登录时间更新。
 *
 * 课程实验不引入额外的安全框架，使用 SHA-256 加随机盐保存密码摘要，
 * 避免把明文密码直接写入数据库。
 */
public final class AccountRepository {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int SALT_BYTES = 16;

    /**
     * 注册账号。
     *
     * @return 注册成功时返回 null，否则返回可以直接显示给用户的错误说明
     */
    public String register(String username, String password) {
        if (!DatabaseInitializer.isAvailable()) {
            return "数据库不可用，请检查 MySQL 服务和账号配置";
        }
        String salt = createSalt();
        String hash = hash(password, salt);
        String sql = """
                INSERT INTO player_account
                (username, password_hash, password_salt, created_at, last_login_at)
                VALUES (?, ?, ?, ?, NULL)
                """;
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            statement.setString(2, hash);
            statement.setString(3, salt);
            statement.setTimestamp(4, new java.sql.Timestamp(System.currentTimeMillis()));
            statement.executeUpdate();
            return null;
        } catch (SQLException exception) {
            // 唯一索引冲突表示用户名已经存在，其他数据库异常统一记录。
            if (isDuplicateKey(exception)) {
                return "用户名已经存在";
            }
            System.err.println("[数据库] 注册账号失败：" + exception.getMessage());
            return "注册失败：" + exception.getMessage();
        }
    }

    /**
     * 校验用户名和密码。验证成功后更新最近登录时间。
     *
     * @return 登录成功时返回 null，否则返回可以直接显示给用户的错误说明
     */
    public String login(String username, String password) {
        if (!DatabaseInitializer.isAvailable()) {
            return "数据库不可用，请检查 MySQL 服务和账号配置";
        }
        String sql = "SELECT id, username, password_hash, password_salt FROM player_account WHERE username = ?";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return "用户名或密码错误";
                }
                PlayerAccount account = new PlayerAccount(
                        result.getLong("id"),
                        result.getString("username"),
                        result.getString("password_hash"),
                        result.getString("password_salt"));
                if (!account.passwordHash().equals(hash(password, account.passwordSalt()))) {
                    return "用户名或密码错误";
                }
                updateLastLogin(connection, account.id());
                return null;
            }
        } catch (SQLException exception) {
            System.err.println("[数据库] 登录失败：" + exception.getMessage());
            return "登录失败：" + exception.getMessage();
        }
    }

    private void updateLastLogin(Connection connection, long accountId) throws SQLException {
        String sql = "UPDATE player_account SET last_login_at = ? WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, new java.sql.Timestamp(System.currentTimeMillis()));
            statement.setLong(2, accountId);
            statement.executeUpdate();
        }
    }

    private boolean isDuplicateKey(SQLException exception) {
        return exception.getErrorCode() == 1062
                || "23000".equals(exception.getSQLState());
    }

    private String createSalt() {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(salt);
    }

    private String hash(String password, String salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(salt.getBytes(StandardCharsets.UTF_8));
            byte[] result = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(result);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", exception);
        }
    }
}
