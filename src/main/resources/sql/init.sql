-- 五子棋数据库初始化脚本，应用启动时会自动执行。
-- 脚本由 JDBC 连接执行，当前连接已经指向配置项 database.name 对应的数据库。

CREATE TABLE IF NOT EXISTS player_account (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    username VARCHAR(50) NOT NULL COMMENT '登录用户名',
    password_hash VARCHAR(128) NOT NULL COMMENT '加盐后的密码摘要',
    password_salt VARCHAR(64) NOT NULL COMMENT '密码盐值',
    created_at DATETIME(3) NOT NULL COMMENT '注册时间',
    last_login_at DATETIME(3) NULL COMMENT '最近登录时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_player_account_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='玩家账号';

CREATE TABLE IF NOT EXISTS game_record (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    game_no VARCHAR(32) NOT NULL COMMENT '对局编号',
    game_mode VARCHAR(20) NOT NULL COMMENT 'LOCAL_PVP/LOCAL_AI/ONLINE',
    black_player VARCHAR(50) NOT NULL COMMENT '黑方名称',
    white_player VARCHAR(50) NOT NULL COMMENT '白方名称',
    winner VARCHAR(10) NOT NULL COMMENT 'BLACK/WHITE/DRAW',
    total_moves INT NOT NULL COMMENT '总步数',
    status VARCHAR(20) NOT NULL DEFAULT 'FINISHED' COMMENT '对局状态',
    started_at DATETIME(3) NOT NULL COMMENT '开始时间',
    ended_at DATETIME(3) NOT NULL COMMENT '结束时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_game_record_no (game_no),
    KEY idx_game_record_time (ended_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对局记录';

CREATE TABLE IF NOT EXISTS game_move (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    game_id BIGINT NOT NULL COMMENT 'game_record.id',
    move_no INT NOT NULL COMMENT '落子序号',
    row_index INT NOT NULL COMMENT '行，范围 0-14',
    col_index INT NOT NULL COMMENT '列，范围 0-14',
    stone_type VARCHAR(10) NOT NULL COMMENT 'BLACK/WHITE',
    moved_at DATETIME(3) NOT NULL COMMENT '落子时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_game_move_no (game_id, move_no),
    KEY idx_game_move_game (game_id),
    CONSTRAINT fk_game_move_game FOREIGN KEY (game_id) REFERENCES game_record(id) ON DELETE CASCADE,
    CONSTRAINT chk_game_move_row CHECK (row_index BETWEEN 0 AND 14),
    CONSTRAINT chk_game_move_col CHECK (col_index BETWEEN 0 AND 14),
    CONSTRAINT chk_game_move_stone CHECK (stone_type IN ('BLACK', 'WHITE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='落子记录';

CREATE TABLE IF NOT EXISTS chat_message (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    game_id BIGINT NOT NULL COMMENT 'game_record.id',
    sender VARCHAR(50) NOT NULL COMMENT '发送者',
    content VARCHAR(500) NOT NULL COMMENT '聊天内容',
    sent_at DATETIME(3) NOT NULL COMMENT '发送时间',
    PRIMARY KEY (id),
    KEY idx_chat_message_game (game_id),
    CONSTRAINT fk_chat_message_game FOREIGN KEY (game_id) REFERENCES game_record(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='聊天记录';
