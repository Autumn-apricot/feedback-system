package com.dongqiuxing.feedback.service.impl;

import com.dongqiuxing.feedback.entity.Feedback;
import com.dongqiuxing.feedback.mapper.FeedbackMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 意见反馈业务层单元测试
 *
 * <p>用 Mockito 把数据库（FeedbackMapper）与消息队列（RabbitTemplate）都替换成替身，
 * 只针对 {@link FeedbackServiceImpl} 自身的职责做断言：补全创建时间、落库、发通知、
 * 以及「通知失败不能影响提交」这条容错策略。</p>
 *
 * <p>这里不加载 Spring 容器，所以跑得很快（毫秒级），适合作为提交前的快速回归。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("意见反馈业务层 · 单元测试")
class FeedbackServiceImplTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private FeedbackMapper feedbackMapper;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private FeedbackServiceImpl feedbackService;

    private static Feedback newFeedback() {
        Feedback feedback = new Feedback();
        feedback.setUsername("张三");
        feedback.setContent("建议增加班车班次");
        return feedback;
    }

    // ------------------------------------------------------------------
    // 提交意见
    // ------------------------------------------------------------------

    @Test
    @DisplayName("UT-01 提交时自动补全创建时间，并返回同一个对象")
    void 提交时补全创建时间() {
        Feedback feedback = newFeedback();
        assertThat(feedback.getCreateTime()).as("调用前创建时间应为空").isNull();

        LocalDateTime before = LocalDateTime.now();
        Feedback returned = feedbackService.submitFeedback(feedback);
        LocalDateTime after = LocalDateTime.now();

        assertThat(returned).isSameAs(feedback);
        assertThat(returned.getCreateTime())
                .as("创建时间应被服务端补全，而不是依赖调用方传入")
                .isNotNull()
                .isAfterOrEqualTo(before.minusSeconds(1))
                .isBeforeOrEqualTo(after.plusSeconds(1));
    }

    @Test
    @DisplayName("UT-02 提交会调用 Mapper 落库一次")
    void 提交会落库() {
        Feedback feedback = newFeedback();

        feedbackService.submitFeedback(feedback);

        verify(feedbackMapper, times(1)).insert(feedback);
    }

    @Test
    @DisplayName("UT-03 落库的创建时间已是补全后的值，不会把 null 写进数据库")
    void 落库时创建时间已补全() {
        Feedback feedback = newFeedback();
        ArgumentCaptor<Feedback> captor = ArgumentCaptor.forClass(Feedback.class);

        feedbackService.submitFeedback(feedback);

        verify(feedbackMapper).insert(captor.capture());
        assertThat(captor.getValue().getCreateTime()).isNotNull();
    }

    // ------------------------------------------------------------------
    // MQ 通知
    // ------------------------------------------------------------------

    @Test
    @DisplayName("UT-04 提交后向 feedback.exchange 发送 JSON 通知，字段完整")
    void 提交后发送JSON通知() throws Exception {
        Feedback feedback = newFeedback();

        feedbackService.submitFeedback(feedback);

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(rabbitTemplate).convertAndSend(eq("feedback.exchange"), eq(""), payload.capture());

        JsonNode node = objectMapper.readTree(String.valueOf(payload.getValue()));
        assertThat(node.path("username").asText()).isEqualTo("张三");
        assertThat(node.path("content").asText()).isEqualTo("建议增加班车班次");
        assertThat(node.path("createTime").asText())
                .as("时间应序列化为字符串而不是数组，否则下游解析会出问题")
                .isNotBlank();
    }

    @Test
    @DisplayName("UT-05 通知属于尽力而为：MQ 抛异常时提交仍成功，异常被吞掉不外抛")
    void MQ异常不影响提交结果() {
        Feedback feedback = newFeedback();
        doThrow(new AmqpException("broker 不可用"))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));

        assertThatCode(() -> feedbackService.submitFeedback(feedback))
                .as("MQ 挂了不应该让用户看到报错")
                .doesNotThrowAnyException();

        verify(feedbackMapper, times(1)).insert(feedback);
        assertThat(feedback.getCreateTime()).isNotNull();
    }

    @Test
    @DisplayName("UT-06 落库在发送通知之前完成：先持久化，后通知")
    void 先落库后发通知() {
        Feedback feedback = newFeedback();

        feedbackService.submitFeedback(feedback);

        org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(feedbackMapper, rabbitTemplate);
        inOrder.verify(feedbackMapper).insert(feedback);
        inOrder.verify(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));
    }

    // ------------------------------------------------------------------
    // 列表查询
    // ------------------------------------------------------------------

    @Test
    @DisplayName("UT-07 列表查询委托给 Mapper，并把结果原样返回")
    void 列表查询委托给Mapper() {
        List<Feedback> expected = Arrays.asList(newFeedback(), newFeedback());
        when(feedbackMapper.selectList(any())).thenReturn(expected);

        List<Feedback> actual = feedbackService.getAllFeedbacks();

        assertThat(actual).isSameAs(expected);
        verify(feedbackMapper, times(1)).selectList(any());
    }

    @Test
    @DisplayName("UT-08 列表为空时返回空集合而非 null，调用方不需要额外判空")
    void 列表为空时返回空集合() {
        when(feedbackMapper.selectList(any())).thenReturn(Collections.emptyList());

        assertThat(feedbackService.getAllFeedbacks()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("UT-09 查询列表不应产生任何写操作与消息发送")
    void 查询列表不产生副作用() {
        when(feedbackMapper.selectList(any())).thenReturn(Collections.emptyList());

        feedbackService.getAllFeedbacks();

        verify(feedbackMapper, never()).insert(any(Feedback.class));
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
    }
}
