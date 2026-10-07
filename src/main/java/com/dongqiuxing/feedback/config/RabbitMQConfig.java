package com.dongqiuxing.feedback.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 配置类
 *
 * <p>声明 Fanout 类型交换器、持久化队列，并把队列绑定到交换器上。</p>
 *
 * <p>选择 Fanout 而不是 Direct：意见提交通知属于广播语义，后续如果新增
 * 「邮件通知」「数据统计」等订阅方，只需再声明一个队列并绑定到同一个交换器，
 * 生产者代码完全不用改。</p>
 */
@Configuration
public class RabbitMQConfig {

    /** Fanout 交换器：忽略路由键，把消息广播给所有绑定的队列 */
    @Bean
    public FanoutExchange feedbackExchange() {
        return new FanoutExchange("feedback.exchange");
    }

    /** 消息队列，durable=true 保证 Broker 重启后队列与消息不丢失 */
    @Bean
    public Queue feedbackQueue() {
        return new Queue("feedback.queue", true);
    }

    /** 把队列绑定到交换器 */
    @Bean
    public Binding bindingFeedbackQueue(FanoutExchange feedbackExchange, Queue feedbackQueue) {
        return BindingBuilder.bind(feedbackQueue).to(feedbackExchange);
    }
}
