package com.dongqiuxing.feedback.consumer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * MQ 通知消费者单元测试
 *
 * <p>{@link FeedbackNoticeConsumer#receiveMessage(String)} 是消息队列的入口，
 * 它的健壮性比功能更重要：一条畸形消息不能让监听线程抛异常，
 * 否则在真实环境里会造成消息反复重投、甚至阻塞整个队列。</p>
 *
 * <p>这里不启动 Spring 容器也不连 Broker，直接构造消费者对象调用方法，
 * 验证「无论收到什么样的消息，都不能把异常抛出去」这条约定。</p>
 */
@DisplayName("MQ 通知消费者 · 单元测试")
class FeedbackNoticeConsumerTest {

    private final FeedbackNoticeConsumer consumer = new FeedbackNoticeConsumer();

    @Test
    @DisplayName("UT-10 收到合法 JSON 通知时能正常解析，不抛异常")
    void 合法JSON消息正常消费() {
        String message = "{\"username\":\"张三\",\"content\":\"建议增加班车班次\","
                + "\"createTime\":\"2026-10-08T09:30:00\"}";

        assertThatCode(() -> consumer.receiveMessage(message)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("UT-11 收到畸形 JSON 时只记日志，不向监听线程抛异常")
    void 畸形JSON不抛异常() {
        assertThatCode(() -> consumer.receiveMessage("{这不是合法的 JSON"))
                .as("解析失败必须被内部消化，否则消息会反复重投")
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("UT-12 消息缺少字段时也能处理，不出现 NPE")
    void 缺少字段不抛异常() {
        assertThatCode(() -> consumer.receiveMessage("{}")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("UT-13 收到空消息体时也不抛异常")
    void 空消息不抛异常() {
        assertThatCode(() -> consumer.receiveMessage("")).doesNotThrowAnyException();
    }
}
