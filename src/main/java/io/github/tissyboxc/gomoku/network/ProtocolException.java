package io.github.tissyboxc.gomoku.network;

/**
 * 网络消息格式不正确时抛出。
 */
public final class ProtocolException extends Exception {
    public ProtocolException(String message) {
        super(message);
    }

    public ProtocolException(String message, Throwable cause) {
        super(message, cause);
    }
}
