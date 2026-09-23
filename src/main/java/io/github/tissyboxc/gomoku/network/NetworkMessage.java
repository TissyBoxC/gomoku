package io.github.tissyboxc.gomoku.network;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.StringJoiner;

/**
 * Socket 文本协议消息。各字段使用 URL 安全的 Base64 编码，
 * 这样聊天内容中的换行、竖线等字符不会破坏协议。
 */
public record NetworkMessage(String type, List<String> fields) {
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    public NetworkMessage {
        type = type == null ? "" : type;
        fields = List.copyOf(fields == null ? List.of() : fields);
    }

    public static NetworkMessage of(String type, String... fields) {
        List<String> values = new ArrayList<>();
        if (fields != null) {
            for (String field : fields) {
                values.add(field == null ? "" : field);
            }
        }
        return new NetworkMessage(type, values);
    }

    public String encode() {
        StringJoiner joiner = new StringJoiner("\t");
        joiner.add(encodeField(type));
        for (String field : fields) {
            joiner.add(encodeField(field));
        }
        return joiner.toString();
    }

    public static NetworkMessage decode(String line) throws ProtocolException {
        if (line == null || line.isBlank()) {
            throw new ProtocolException("消息为空");
        }
        String[] parts = line.split("\t", -1);
        if (parts.length == 0) {
            throw new ProtocolException("消息缺少类型");
        }
        try {
            String type = decodeField(parts[0]);
            List<String> fields = new ArrayList<>();
            for (int i = 1; i < parts.length; i++) {
                fields.add(decodeField(parts[i]));
            }
            return new NetworkMessage(type, fields);
        } catch (IllegalArgumentException exception) {
            throw new ProtocolException("消息编码无法解析", exception);
        }
    }

    public String field(int index) {
        return index >= 0 && index < fields.size() ? fields.get(index) : "";
    }

    public int intField(int index, int defaultValue) {
        try {
            return Integer.parseInt(field(index));
        } catch (NumberFormatException exception) {
            return defaultValue;
        }
    }

    private static String encodeField(String value) {
        return ENCODER.encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decodeField(String value) {
        return new String(DECODER.decode(value), StandardCharsets.UTF_8);
    }
}
