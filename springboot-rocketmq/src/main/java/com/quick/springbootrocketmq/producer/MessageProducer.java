package com.quick.springbootrocketmq.producer;

import com.quick.springbootrocketmq.model.MessageWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.TransactionSendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.spring.support.RocketMQHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * RocketMQ 消息生产者 —— 封装全部发送方式。
 * <p>
 * RocketMQ 发送模式对比：
 * <ul>
 *   <li><b>同步发送</b> —— 等待 Broker 确认，可靠性最高，有重试</li>
 *   <li><b>异步发送</b> —— 不阻塞线程，通过回调处理结果</li>
 *   <li><b>单向发送</b> —— 不关心结果，吞吐最高（如日志上报）</li>
 *   <li><b>顺序发送</b> —— 相同 hashKey 的消息路由到同一队列，保证 FIFO</li>
 *   <li><b>延迟发送</b> —— 18 个内置延迟等级（1s ~ 2h）</li>
 *   <li><b>事务发送</b> —— Half-Message 二阶段提交</li>
 * </ul>
 * <p>
 * destination 格式：{@code "topic:tag"}，如 {@code "order-topic:order-created"}。
 * Tag 用于消费端过滤，类似 RabbitMQ 的 routing key。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageProducer {

    private final RocketMQTemplate rocketMQTemplate;

    // ==================== 同步发送（最常用，等待结果） ====================

    /**
     * 同步发送 —— 等待 Broker 确认后返回。
     * <pre>
     * SendResult result = producer.syncSend("order-topic:order-created", orderMsg);
     * log.info("发送成功: msgId={}, queue={}", result.getMsgId(), result.getMessageQueue());
     * </pre>
     */
    public SendResult syncSend(String topic, String tag, Object payload) {
        String destination = buildDestination(topic, tag);
        MessageWrapper<?> wrapper = MessageWrapper.of(tag, payload);
        SendResult result = rocketMQTemplate.syncSend(destination, wrapper);
        log.debug("同步发送成功: destination={}, msgId={}, queue={}",
                destination, result.getMsgId(), result.getMessageQueue().getQueueId());
        return result;
    }

    /** 同步发送（便捷版，使用 MessageWrapper） */
    public SendResult syncSend(String topic, String tag, MessageWrapper<?> wrapper) {
        String destination = buildDestination(topic, tag);
        SendResult result = rocketMQTemplate.syncSend(destination, wrapper);
        log.debug("同步发送成功: destination={}, msgId={}", destination, wrapper.getMessageId());
        return result;
    }

    // ==================== 异步发送（非阻塞，回调处理） ====================

    /**
     * 异步发送 —— 不阻塞调用线程，通过回调处理成功/失败。
     * <pre>
     * producer.asyncSend("order-topic", "order-created", payload,
     *     result -> log.info("发送成功: {}", result.getMsgId()),
     *     ex -> log.error("发送失败", ex));
     * </pre>
     */
    public void asyncSend(String topic, String tag, Object payload,
                          java.util.function.Consumer<SendResult> onSuccess,
                          java.util.function.Consumer<Throwable> onFailure) {
        String destination = buildDestination(topic, tag);
        MessageWrapper<?> wrapper = MessageWrapper.of(tag, payload);
        rocketMQTemplate.asyncSend(destination, wrapper, new SendCallback() {
            @Override
            public void onSuccess(SendResult result) {
                log.debug("异步发送成功: destination={}, msgId={}", destination, result.getMsgId());
                if (onSuccess != null) onSuccess.accept(result);
            }
            @Override
            public void onException(Throwable e) {
                log.error("异步发送失败: destination={}", destination, e);
                if (onFailure != null) onFailure.accept(e);
            }
        });
    }

    // ==================== 单向发送（不关心结果，吞吐最高） ====================

    /**
     * 单向发送 —— 不等待任何响应，发完即忘。
     * <p>适用场景：日志上报、非关键埋点等丢失可接受的场景。
     */
    public void sendOneWay(String topic, String tag, Object payload) {
        String destination = buildDestination(topic, tag);
        MessageWrapper<?> wrapper = MessageWrapper.of(tag, payload);
        rocketMQTemplate.sendOneWay(destination, wrapper);
        log.debug("单向发送完成: destination={}", destination);
    }

    // ==================== 顺序发送（相同 hashKey → 同一队列 → FIFO） ====================

    /**
     * 顺序发送 —— 相同 hashKey 的消息路由到同一队列，保证消费顺序。
     * <p>
     * 典型场景：同一订单的创建 → 支付 → 发货 必须按顺序处理。
     * <pre>
     * // 用 orderId 作为 hashKey，保证同一订单的消息顺序
     * producer.syncSendOrderly("order-topic", "order-status", orderMsg, String.valueOf(orderId));
     * </pre>
     */
    public SendResult syncSendOrderly(String topic, String tag, Object payload, String hashKey) {
        String destination = buildDestination(topic, tag);
        MessageWrapper<?> wrapper = MessageWrapper.of(tag, hashKey, payload);
        SendResult result = rocketMQTemplate.syncSendOrderly(destination, wrapper, hashKey);
        log.debug("顺序发送成功: destination={}, hashKey={}, queueId={}",
                destination, hashKey, result.getMessageQueue().getQueueId());
        return result;
    }

    // ==================== 延迟发送 ====================

    /**
     * 延迟发送 —— 支持任意秒数（RocketMQ 5.x 起原生支持）。
     * <p>RocketMQ 4.x 用户请使用 {@link #syncSendDelayLevel(String, String, Object, int)}。
     *
     * @param delaySeconds 延迟秒数
     */
    public SendResult syncSendDelay(String topic, String tag, Object payload, long delaySeconds) {
        String destination = buildDestination(topic, tag);
        MessageWrapper<?> wrapper = MessageWrapper.of(tag, payload);
        SendResult result = rocketMQTemplate.syncSendDelayTimeSeconds(destination, wrapper, delaySeconds);
        log.debug("延迟发送成功: destination={}, delay={}s, msgId={}",
                destination, delaySeconds, result.getMsgId());
        return result;
    }

    /**
     * 延迟发送（RocketMQ 4.x 兼容版 —— 18 个固定延迟等级）。
     * <pre>
     * 1=1s    2=5s    3=10s   4=30s   5=1m    6=2m
     * 7=3m    8=4m    9=5m    10=6m   11=7m   12=8m
     * 13=9m   14=10m  15=20m  16=30m  17=1h   18=2h
     * </pre>
     *
     * @param delayLevel 延迟等级（1-18）
     */
    public SendResult syncSendDelayLevel(String topic, String tag, Object payload, int delayLevel) {
        if (delayLevel < 1 || delayLevel > 18) {
            throw new IllegalArgumentException("延迟等级必须在 1-18 之间");
        }
        String destination = buildDestination(topic, tag);
        Message<?> message = buildSpringMessage(wrapperData(tag, payload));
        // syncSend(destination, message, timeout, delayLevel)
        SendResult result = rocketMQTemplate.syncSend(destination, message, 3000L, delayLevel);
        log.debug("延迟发送成功: destination={}, delayLevel={}, msgId={}",
                destination, delayLevel, result.getMsgId());
        return result;
    }

    // ==================== 事务发送（Half-Message 二阶段） ====================

    /**
     * 事务消息 —— 先发 Half-Message，执行本地事务后 commit 或 rollback。
     * <p>
     * 流程：
     * <ol>
     *   <li>发送 Half-Message 到 Broker（此时消费者不可见）</li>
     *   <li>执行本地事务（如写入数据库）</li>
     *   <li>本地事务成功 → commit（消息可见，消费者可消费）</li>
     *   <li>本地事务失败 → rollback（消息删除）</li>
     *   <li>若 commit/rollback 超时 → Broker 回调 checkLocalTransaction 做事务回查</li>
     * </ol>
     * <p>
     * 使用前提：需要注册一个 {@link org.apache.rocketmq.client.producer.TransactionListener} Bean，
     * 并在 {@code executeLocalTransaction()} 中编写本地事务逻辑。
     *
     * @param arg 传给 TransactionListener.executeLocalTransaction() 的附加参数
     */
    public TransactionSendResult sendMessageInTransaction(String topic, String tag,
                                                          Object payload, Object arg) {
        String destination = buildDestination(topic, tag);
        MessageWrapper<?> wrapper = MessageWrapper.of(tag, payload);

        Message<?> message = MessageBuilder
                .withPayload(wrapper)
                .setHeader(RocketMQHeaders.TRANSACTION_ID, wrapper.getMessageId())
                .setHeader(RocketMQHeaders.KEYS, wrapper.getMessageKey())
                .setHeader(RocketMQHeaders.TAGS, tag)
                .build();

        TransactionSendResult result = rocketMQTemplate.sendMessageInTransaction(
                destination, message, arg);
        log.debug("事务消息发送: destination={}, msgId={}, status={}",
                destination, wrapper.getMessageId(), result.getSendStatus());
        return result;
    }

    // ==================== 批量发送 ====================

    /**
     * 批量同步发送 —— 将多条消息合并为一次网络请求。
     * <p>注意：总大小不能超过 max-message-size（默认 4MB）。
     */
    public void syncSendBatch(String topic, String tag, List<MessageWrapper<?>> wrappers) {
        String destination = buildDestination(topic, tag);
        List<Message<?>> messages = new ArrayList<>();
        for (MessageWrapper<?> wrapper : wrappers) {
            messages.add(buildSpringMessage(wrapper));
        }
        rocketMQTemplate.syncSend(destination, messages);
        log.debug("批量发送完成: destination={}, count={}", destination, wrappers.size());
    }

    // ==================== 通用便捷方法 ====================

    /**
     * 通用发送（同步，最常用的一个方法）。
     * <pre>
     * producer.send("order-topic", "order-created", orderMsg);
     * </pre>
     */
    public void send(String topic, String tag, Object payload) {
        syncSend(topic, tag, payload);
    }

    // ==================== 内部工具方法 ====================

    /** 构建 destination "{topic}:{tag}" */
    private String buildDestination(String topic, String tag) {
        return topic + ":" + tag;
    }

    /** 构造带有必要 Header 的 Spring Message */
    private Message<?> buildSpringMessage(MessageWrapper<?> wrapper) {
        return MessageBuilder
                .withPayload(wrapper)
                .setHeader(RocketMQHeaders.KEYS, wrapper.getMessageKey())
                .setHeader(RocketMQHeaders.MESSAGE_ID, wrapper.getMessageId())
                .build();
    }

    /** 快速构造 MessageWrapper */
    private MessageWrapper<Object> wrapperData(String tag, Object payload) {
        return MessageWrapper.of(tag, payload);
    }
}
