-- ==========================================
-- 测试库建表脚本（H2，MySQL 兼容模式）
--
-- 与 src/main/resources/sql/schema.sql 的 feedback 表保持同构：
--   username VARCHAR(50) —— 正是「用户名超长返回 500」这条缺陷的触发条件
--   content  CLOB/TEXT   —— 长度上限远大于 username，所以 content 超长不会报错
-- 刻意不加 NOT NULL 之外的任何约束，保证测试观察到的就是生产环境的行为。
-- ==========================================

DROP TABLE IF EXISTS feedback;

CREATE TABLE feedback (
    id          INT         AUTO_INCREMENT PRIMARY KEY,
    username    VARCHAR(50) NOT NULL,
    content     CLOB        NOT NULL,
    create_time TIMESTAMP   DEFAULT CURRENT_TIMESTAMP
);
