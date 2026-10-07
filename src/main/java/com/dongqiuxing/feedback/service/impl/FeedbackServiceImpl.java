package com.dongqiuxing.feedback.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.dongqiuxing.feedback.entity.Feedback;
import com.dongqiuxing.feedback.mapper.FeedbackMapper;
import com.dongqiuxing.feedback.service.FeedbackService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 意见反馈业务实现
 *
 * <p>这里集中体现了三个关注点：</p>
 * <ul>
 *   <li><b>落库</b>：MyBatis-Plus 的 insert 自动回填自增主键</li>
 *   <li><b>缓存</b>：列表查询走 @Cacheable，提交成功后用 @CacheEvict 整体失效，
 *       保证用户提交完立刻能在列表页看到自己的意见</li>
 *   <li><b>异步通知</b>：提交成功后发 MQ 消息，通知逻辑不占用用户请求线程</li>
 * </ul>
 */
@Slf4j
@Service
public class FeedbackServiceImpl implements FeedbackService {

    @Autowired
    private FeedbackMapper feedbackMapper;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 提交意见
     *
     * <p>@CacheEvict 里的 allEntries = true：列表缓存以整个 List 为粒度，
     * 任何一条新增都会让旧缓存失效，所以直接清空而不是逐条更新。</p>
     */
    @Override
    @CacheEvict(value = "feedbackList", allEntries = true)
    public Feedback submitFeedback(Feedback feedback) {
        feedback.setCreateTime(LocalDateTime.now());
        feedbackMapper.insert(feedback);
        log.info("意见已落库: id={}, 提交人={}", feedback.getId(), feedback.getUsername());

        sendNotice(feedback);
        return feedback;
    }

    /**
     * 查询全部意见（Redis 缓存）
     *
     * <p>第二次访问命中缓存时不会进这个方法，所以下面这行日志只会在缓存未命中时打印，
     * 可直接用它来判断缓存是否生效。</p>
     */
    @Override
    @Cacheable(value = "feedbackList")
    public List<Feedback> getAllFeedbacks() {
        log.info("缓存未命中，回源查询数据库获取意见列表");
        return feedbackMapper.selectList(null);
    }

    /**
     * 发送意见提交通知到 RabbitMQ
     *
     * <p>通知属于「尽力而为」的附加动作：MQ 挂了不该导致用户提交失败，
     * 所以这里单独 try-catch，序列化或发送异常只记日志、不向上抛。</p>
     */
    private void sendNotice(Feedback feedback) {
        try {
            Map<String, Object> message = new HashMap<>();
            message.put("username", feedback.getUsername());
            message.put("content", feedback.getContent());
            message.put("createTime", feedback.getCreateTime().toString());

            String json = objectMapper.writeValueAsString(message);
            rabbitTemplate.convertAndSend("feedback.exchange", "", json);
            log.info("已发送意见通知到 RabbitMQ: {}", json);
        } catch (Exception e) {
            log.error("意见通知发送失败，不影响提交结果", e);
        }
    }
}
