package com.dongqiuxing.feedback.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 意见反馈实体，映射 MySQL 的 feedback 表
 *
 * <p>注意：实体是要被写进 Redis 缓存、并作为 JSON 消息发到 RabbitMQ 的，
 * 所以 LocalDateTime 字段用 @JsonFormat 固定日期格式，避免序列化出数组结构。</p>
 */
@Data
@TableName("feedback")
public class Feedback {

    /** 主键，数据库自增 */
    @TableId(type = IdType.AUTO)
    private Integer id;

    /** 提交人姓名 */
    private String username;

    /** 意见内容 */
    private String content;

    /** 提交时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
