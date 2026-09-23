package io.github.tissyboxc.gomoku.database;

import io.github.tissyboxc.gomoku.core.GameSession;
import io.github.tissyboxc.gomoku.model.ChatMessage;
import io.github.tissyboxc.gomoku.model.GameMode;
import io.github.tissyboxc.gomoku.model.Move;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

/**
 * 对局数据访问类，使用 JDBC 保存棋局、落子和聊天记录。
 */
public final class GameRepository {
    public void saveCompletedGame(
            GameSession session,
            GameMode mode,
            String blackName,
            String whiteName,
            List<ChatMessage> chatMessages) {
        if (!DatabaseInitializer.isAvailable()) {
            return;
        }

        String gameNo = UUID.randomUUID().toString().replace("-", "");
        String winner = session.getWinner() == io.github.tissyboxc.gomoku.model.Stone.EMPTY
                ? "DRAW"
                : session.getWinner().name();

        try (Connection connection = DatabaseManager.getConnection()) {
            connection.setAutoCommit(false);
            try {
                long gameId = insertGame(connection, gameNo, mode, blackName, whiteName, winner, session);
                insertMoves(connection, gameId, session.getMoves());
                insertChats(connection, gameId, chatMessages);
                connection.commit();
                System.out.println("[数据库] 已保存对局：" + gameNo);
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            System.err.println("[数据库] 保存对局失败：" + exception.getMessage());
        }
    }

    private long insertGame(
            Connection connection,
            String gameNo,
            GameMode mode,
            String blackName,
            String whiteName,
            String winner,
            GameSession session) throws SQLException {
        String sql = """
                INSERT INTO game_record
                (game_no, game_mode, black_player, white_player, winner, total_moves, status, started_at, ended_at)
                VALUES (?, ?, ?, ?, ?, ?, 'FINISHED', ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            long now = System.currentTimeMillis();
            statement.setString(1, gameNo);
            statement.setString(2, mode.name());
            statement.setString(3, blackName);
            statement.setString(4, whiteName);
            statement.setString(5, winner);
            statement.setInt(6, session.getMoveCount());
            statement.setTimestamp(7, new Timestamp(now));
            statement.setTimestamp(8, new Timestamp(System.currentTimeMillis()));
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        throw new SQLException("未能取得对局主键");
    }

    private void insertMoves(Connection connection, long gameId, List<Move> moves) throws SQLException {
        String sql = """
                INSERT INTO game_move
                (game_id, move_no, row_index, col_index, stone_type, moved_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int moveNo = 1;
            for (Move move : moves) {
                statement.setLong(1, gameId);
                statement.setInt(2, moveNo++);
                statement.setInt(3, move.row());
                statement.setInt(4, move.col());
                statement.setString(5, move.stone().name());
                statement.setTimestamp(6, new Timestamp(move.timeMillis()));
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void insertChats(Connection connection, long gameId, List<ChatMessage> chats) throws SQLException {
        if (chats == null || chats.isEmpty()) {
            return;
        }
        String sql = "INSERT INTO chat_message (game_id, sender, content, sent_at) VALUES (?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (ChatMessage chat : chats) {
                statement.setLong(1, gameId);
                statement.setString(2, chat.sender());
                statement.setString(3, chat.content());
                statement.setTimestamp(4, new Timestamp(chat.timeMillis()));
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }
}
