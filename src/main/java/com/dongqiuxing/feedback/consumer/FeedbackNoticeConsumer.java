package com.dongqiuxing.feedback.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 意见提交通知消费者
 *
 * <p>监听 feedback.queue，把生产者发来的 JSON 消息解析后打印。
 * 真实业务中这里可以替换为发邮件、推企业微信、写审计日志等下游动作，
 * 生产者（FeedbackServiceImpl）不需要做任何改动。</p>
 */
@Slf4j
@Component
public class FeedbackNoticeConsumer {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @RabbitListener(queues = "feedback.queue")
    public void receiveMessage(String message) {
        log.info("收到新意见通知: {}", message);
        try {
            JsonNode node = objectMapper.readTree(message);
            log.info("通知详情 -> 提交人: {} | 提交时间: {}",
                    node.path("username").asText(),
                    node.path("createTime").asText());
        } catch (Exception e) {
            log.warn("通知消息解析失败: " + message, e);
        }
    }
}
