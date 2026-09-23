package io.github.tissyboxc.gomoku.model;

/**
 * 聊天消息，联机服务器结束时可将聊天内容写入数据库。
 */
public record ChatMessage(String sender, String content, long timeMillis) {

    public ChatMessage(String sender, String content) {
        this(sender, content, System.currentTimeMillis());
    }
}
