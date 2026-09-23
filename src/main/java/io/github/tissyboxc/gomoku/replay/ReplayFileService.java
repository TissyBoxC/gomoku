package io.github.tissyboxc.gomoku.replay;

import io.github.tissyboxc.gomoku.core.GameSession;
import io.github.tissyboxc.gomoku.model.GameMode;
import io.github.tissyboxc.gomoku.model.Move;
import io.github.tissyboxc.gomoku.model.Stone;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 对局文件读写服务。文件采用简单的文本行格式，便于直接查看和答辩演示。
 */
public final class ReplayFileService {
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    public Path save(GameSession session, GameMode mode, String blackName, String whiteName) throws IOException {
        Path directory = replayDirectory();
        Files.createDirectories(directory);
        String fileName = "gomoku-" + FILE_TIME.format(LocalDateTime.now()) + ".gomoku";
        Path file = directory.resolve(fileName);

        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            writer.write("# GOMOKU_REPLAY_V1");
            writer.newLine();
            writer.write("MODE=" + mode.name());
            writer.newLine();
            writer.write("BLACK=" + safe(blackName));
            writer.newLine();
            writer.write("WHITE=" + safe(whiteName));
            writer.newLine();
            writer.write("WINNER=" + session.getWinner().name());
            writer.newLine();
            for (Move move : session.getMoves()) {
                writer.write("MOVE=" + move.row() + "," + move.col() + "," + move.stone().name()
                        + "," + move.timeMillis());
                writer.newLine();
            }
        }
        return file;
    }

    public ReplayData load(Path file) throws IOException {
        List<Move> moves = new ArrayList<>();
        GameMode mode = GameMode.LOCAL_PVP;
        String blackName = "黑方";
        String whiteName = "白方";
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String value = line.trim();
                if (value.isEmpty() || value.startsWith("#")) {
                    continue;
                }
                if (value.startsWith("MODE=")) {
                    mode = parseMode(value.substring(5));
                } else if (value.startsWith("BLACK=")) {
                    blackName = value.substring(6);
                } else if (value.startsWith("WHITE=")) {
                    whiteName = value.substring(6);
                } else if (value.startsWith("MOVE=")) {
                    Move move = parseMove(value.substring(5));
                    if (move != null) {
                        moves.add(move);
                    }
                }
            }
        }
        return new ReplayData(mode, blackName, whiteName, moves);
    }

    public Path replayDirectory() {
        return Path.of(System.getProperty("user.dir"), "replays");
    }

    private GameMode parseMode(String value) {
        try {
            return GameMode.valueOf(value);
        } catch (IllegalArgumentException exception) {
            return GameMode.LOCAL_PVP;
        }
    }

    private Move parseMove(String value) {
        String[] parts = value.split(",");
        if (parts.length < 3) {
            return null;
        }
        try {
            int row = Integer.parseInt(parts[0]);
            int col = Integer.parseInt(parts[1]);
            Stone stone = Stone.valueOf(parts[2]);
            long time = parts.length > 3 ? Long.parseLong(parts[3]) : System.currentTimeMillis();
            return new Move(row, col, stone, time);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private String safe(String value) {
        return value == null ? "" : value.replace("\n", " ").replace("\r", " ");
    }

    public record ReplayData(GameMode mode, String blackName, String whiteName, List<Move> moves) {
        public ReplayData {
            moves = List.copyOf(moves);
        }
    }
}
