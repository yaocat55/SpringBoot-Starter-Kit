package com.quick.springrabbitmq.producer;

import com.quick.springrabbitmq.config.RabbitQueueConfig;
import com.quick.springrabbitmq.model.MessageWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 消息发布器 —— 封装所有发送模式。
 * <p>
 * 发送模式：
 * <ul>
 *   <li>{@code send} —— 基础发送，不关心结果</li>
 *   <li>{@code sendWithConfirm} —— 带 Confirm 回调的发送（异步确认到达 Exchange）</li>
 *   <li>{@code sendWithDelay} —— 延迟发送（基于 TTL + 死信）</li>
 *   <li>{@code sendWithPriority} —— 带消息优先级的发送</li>
 *   <li>{@code sendWithExpiration} —— 单条消息 TTL</li>
 *   <li>{@code convertAndSend} —— 对象自动 JSON 序列化（最常用）</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessagePublisher {

    private final RabbitTemplate rabbitTemplate;

    // ==================== Direct 模式 —— 精确匹配 ====================

    /** 发送到 Direct 交换机（对象 → JSON 自动序列化，最常用） */
    public void sendDirect(String routingKey, Object payload) {
        MessageWrapper<?> wrapper = MessageWrapper.of("direct", payload);
        CorrelationData correlationData = new CorrelationData(wrapper.getMessageId());
        rabbitTemplate.convertAndSend(RabbitQueueConfig.DIRECT_EXCHANGE, routingKey, wrapper, correlationData);
        log.debug("Direct 消息已发送: routingKey={}, messageId={}", routingKey, wrapper.getMessageId());
    }

    // ==================== Fanout 模式 —— 广播 ====================

    /** 发送到 Fanout 交换机（routing key 无意义，所有绑定队列都会收到） */
    public void sendFanout(Object payload) {
        MessageWrapper<?> wrapper = MessageWrapper.of("fanout", payload);
        CorrelationData correlationData = new CorrelationData(wrapper.getMessageId());
        rabbitTemplate.convertAndSend(RabbitQueueConfig.FANOUT_EXCHANGE, "", wrapper, correlationData);
        log.debug("Fanout 广播消息已发送: messageId={}", wrapper.getMessageId());
    }

    // ==================== Topic 模式 —— 通配符 ====================

    /** 发送到 Topic 交换机 */
    public void sendTopic(String routingKey, Object payload) {
        MessageWrapper<?> wrapper = MessageWrapper.of("topic", payload);
        CorrelationData correlationData = new CorrelationData(wrapper.getMessageId());
        rabbitTemplate.convertAndSend(RabbitQueueConfig.TOPIC_EXCHANGE, routingKey, wrapper, correlationData);
        log.debug("Topic 消息已发送: routingKey={}, messageId={}", routingKey, wrapper.getMessageId());
    }

    // ==================== 延迟消息 ====================

    /**
     * 发送延迟消息（基于 TTL + 死信实现）。
     *
     * @param delayMillis 延迟毫秒数
     */
    public void sendWithDelay(Object payload, long delayMillis) {
        MessageWrapper<?> wrapper = MessageWrapper.of("delay", payload);
        Message message = MessageBuilder
                .withBody(rabbitTemplate.getMessageConverter()
                        .toMessage(wrapper, new MessageProperties()).getBody())
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setMessageId(wrapper.getMessageId())
                .setExpiration(String.valueOf(delayMillis))  // 单条消息 TTL
                .build();

        CorrelationData correlationData = new CorrelationData(wrapper.getMessageId());
        rabbitTemplate.send(RabbitQueueConfig.DELAY_EXCHANGE,
                RabbitQueueConfig.DELAY_ROUTING_KEY, message, correlationData);
        log.debug("延迟消息已发送: delay={}ms, messageId={}", delayMillis, wrapper.getMessageId());
    }

    // ==================== 优先级消息 ====================

    /**
     * 发送带优先级的消息（优先级 0-255，数字越大越优先，需队列开启 x-max-priority）。
     */
    public void sendWithPriority(String exchange, String routingKey, Object payload, int priority) {
        MessageWrapper<?> wrapper = MessageWrapper.of("priority", payload);
        Message message = MessageBuilder
                .withBody(rabbitTemplate.getMessageConverter()
                        .toMessage(wrapper, new MessageProperties()).getBody())
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setMessageId(wrapper.getMessageId())
                .setPriority(priority)
                .build();

        CorrelationData correlationData = new CorrelationData(wrapper.getMessageId());
        rabbitTemplate.send(exchange, routingKey, message, correlationData);
        log.debug("优先级消息已发送: priority={}, messageId={}", priority, wrapper.getMessageId());
    }

    // ==================== 单条消息 TTL ====================

    /**
     * 发送带 TTL 的消息（超时未被消费则自动删除或进死信）。
     */
    public void sendWithExpiration(String exchange, String routingKey, Object payload, long expirationMillis) {
        MessageWrapper<?> wrapper = MessageWrapper.of("expiration", payload);
        Message message = MessageBuilder
                .withBody(rabbitTemplate.getMessageConverter()
                        .toMessage(wrapper, new MessageProperties()).getBody())
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setMessageId(wrapper.getMessageId())
                .setExpiration(String.valueOf(expirationMillis))
                .build();

        CorrelationData correlationData = new CorrelationData(wrapper.getMessageId());
        rabbitTemplate.send(exchange, routingKey, message, correlationData);
        log.debug("TTL 消息已发送: expiration={}ms, messageId={}", expirationMillis, wrapper.getMessageId());
    }

    // ==================== RPC 模式 ====================

    /**
     * 同步 RPC 调用（阻塞等待响应）。
     * <p>
     * 注意：会阻塞当前线程，不适合高并发场景，一般用于需要即时返回结果的场景。
     *
     * @return 响应消息体
     */
    public Object sendAndReceive(String exchange, String routingKey, Object payload) {
        MessageWrapper<?> wrapper = MessageWrapper.of("rpc", payload);
        Object response = rabbitTemplate.convertSendAndReceive(exchange, routingKey, wrapper);
        log.debug("RPC 调用完成: routingKey={}, messageId={}", routingKey, wrapper.getMessageId());
        return response;
    }

    // ==================== 通用便捷方法 ====================

    /**
     * 通用发送 —— 指定 exchange + routingKey + payload。
     * 开发中最常用的一个方法。
     */
    public void send(String exchange, String routingKey, Object payload) {
        MessageWrapper<?> wrapper = MessageWrapper.of("general", payload);
        CorrelationData correlationData = new CorrelationData(wrapper.getMessageId());
        rabbitTemplate.convertAndSend(exchange, routingKey, wrapper, correlationData);
        log.debug("消息已发送: exchange={}, routingKey={}, messageId={}",
                exchange, routingKey, wrapper.getMessageId());
    }
}
