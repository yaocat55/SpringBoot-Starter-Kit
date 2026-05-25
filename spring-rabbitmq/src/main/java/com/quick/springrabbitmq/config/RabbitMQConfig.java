package com.quick.springrabbitmq.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 核心配置 —— RabbitTemplate + MessageConverter + 确认/回调机制。
 * <p>
 * 三个关键回调：
 * <ol>
 *   <li><b>ConfirmCallback</b>：消息到达 Broker（Exchange）后的确认</li>
 *   <li><b>ReturnsCallback</b>：消息无法路由到队列时的回调（mandatory=true 时生效）</li>
 *   <li><b>Manual ACK</b>：消费者手动确认（在 Listener 侧配置）</li>
 * </ol>
 */
@Slf4j
@Configuration
public class RabbitMQConfig {

    // ==================== MessageConverter ====================

    /**
     * JSON 消息转换器 —— 替代默认的 SimpleMessageConverter（Java 序列化），
     * 发送对象自动序列化为 JSON，消费时自动反序列化。
     */
    @Bean
    public MessageConverter messageConverter() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    // ==================== RabbitTemplate ====================

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);

        // --- ConfirmCallback：消息是否成功到达 Exchange ---
        template.setConfirmCallback((CorrelationData correlationData, boolean ack, String cause) -> {
            if (correlationData == null) return;
            if (ack) {
                log.debug("消息到达 Exchange: id={}", correlationData.getId());
            } else {
                log.warn("消息未到达 Exchange: id={}, cause={}", correlationData.getId(), cause);
                // 生产环境：记录到数据库，定时任务补偿重发
            }
        });

        // --- ReturnsCallback：消息路由到队列失败时回调 ---
        template.setReturnsCallback((ReturnedMessage returned) -> {
            log.warn("消息路由失败: exchange={}, routingKey={}, replyCode={}, replyText={}, body={}",
                    returned.getExchange(), returned.getRoutingKey(),
                    returned.getReplyCode(), returned.getReplyText(),
                    new String(returned.getMessage().getBody()));
            // 生产环境：记录到数据库或发送告警
        });

        return template;
    }
}
