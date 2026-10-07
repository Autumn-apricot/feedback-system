-- ==========================================
-- 企业内部意见反馈系统 - 建表脚本
-- 执行方式: mysql -u root -p < schema.sql
-- ==========================================

CREATE DATABASE IF NOT EXISTS feedback_db
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_general_ci;

USE feedback_db;

DROP TABLE IF EXISTS feedback;

CREATE TABLE feedback (
    id          INT         PRIMARY KEY AUTO_INCREMENT  COMMENT '主键ID，自增',
    username    VARCHAR(50) NOT NULL                    COMMENT '提交人姓名',
    content     TEXT        NOT NULL                    COMMENT '意见内容',
    create_time DATETIME    DEFAULT CURRENT_TIMESTAMP   COMMENT '提交时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='意见反馈表';

-- 初始化几条数据，方便启动后直接看到列表页效果
INSERT INTO feedback (username, content, create_time) VALUES
('张伟', '希望能增加移动端打卡功能，现在只能在电脑上操作，出差时不方便。', NOW()),
('李娜', '食堂的排队时间有点长，建议错峰开放或者增加窗口。', NOW()),
('王强', '办公区空调温度偏低，靠窗的位置比较冷，建议分区控制。', NOW());
