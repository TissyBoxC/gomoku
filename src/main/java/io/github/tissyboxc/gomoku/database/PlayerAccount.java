package io.github.tissyboxc.gomoku.database;

/**
 * 玩家账号实体，仅保存数据库查询需要的基础字段。
 */
public record PlayerAccount(long id, String username, String passwordHash, String passwordSalt) {
}
