package com.quick.springbootrocketmq.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 统一消息包装体 —— 所有发送到 RocketMQ 的消息都套此结构。
 * <p>
 * 标准化消息格式的好处：
 * <ul>
 *   <li>{@link #messageId} —— 全局唯一标识，用于链路追踪和幂等去重</li>
 *   <li>{@link #messageKey} —— RocketMQ 的 Key，相同 Key 的消息会路由到同一队列（顺序消息）</li>
 *   <li>{@link #timestamp} —— 消息产生时间，方便排查延迟</li>
 *   <li>{@link #source} —— 标识消息来源服务</li>
 *   <li>{@link #messageType} —— 消息类型常量，消费方可据此路由</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageWrapper<T> implements Serializable {

    /** 消息唯一 ID（业务层生成，不同于 RocketMQ 的 msgId） */
    @Builder.Default
    private String messageId = UUID.randomUUID().toString().replace("-", "");

    /** RocketMQ Message Key（相同 Key 路由到同一队列，用于顺序消息 / 查询） */
    private String messageKey;

    /** 消息来源服务名 */
    @Builder.Default
    private String source = "springboot-rocketmq";

    /** 消息产生时间 */
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    /** 消息类型 */
    private String messageType;

    /** 消息体 */
    private T payload;

    /** 快捷构建 */
    public static <T> MessageWrapper<T> of(String messageType, T payload) {
        return MessageWrapper.<T>builder()
                .messageType(messageType)
                .payload(payload)
                .build();
    }

    /** 快捷构建（带 messageKey，用于顺序消息） */
    public static <T> MessageWrapper<T> of(String messageType, String messageKey, T payload) {
        return MessageWrapper.<T>builder()
                .messageType(messageType)
                .messageKey(messageKey)
                .payload(payload)
                .build();
    }
}
