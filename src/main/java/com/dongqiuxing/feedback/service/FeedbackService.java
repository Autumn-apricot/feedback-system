package com.dongqiuxing.feedback.service;

import com.dongqiuxing.feedback.entity.Feedback;

import java.util.List;

/**
 * 意见反馈业务接口
 */
public interface FeedbackService {

    /**
     * 提交一条意见，落库后异步发送 MQ 通知，并清除列表缓存
     */
    Feedback submitFeedback(Feedback feedback);

    /**
     * 查询全部意见列表（带 Redis 缓存）
     */
    List<Feedback> getAllFeedbacks();
}
