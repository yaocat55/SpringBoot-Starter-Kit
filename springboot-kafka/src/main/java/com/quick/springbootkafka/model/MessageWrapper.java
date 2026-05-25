package com.quick.springbootkafka.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 通用消息包装类 —— 统一消息格式，便于跨服务解析和链路追踪。
 * <p>
 * Kafka 消息体建议使用统一 {@code MessageWrapper<T>} 包装：
 * <ul>
 *   <li>{@code messageId} —— 消息唯一 ID，关联业务流水，用于去重和链路追踪</li>
 *   <li>{@code messageKey} —— Kafka 分区路由键，相同 Key 的消息进入同一分区保证顺序</li>
 *   <li>{@code source} —— 来源服务标识，便于排查</li>
 *   <li>{@code timestamp} —— 消息时间戳，消费者可计算端到端延迟</li>
 *   <li>{@code messageType} —— 消息类型，用于消费者做路由分发</li>
 *   <li>{@code payload} —— 实际业务数据</li>
 * </ul>
 *
 * @param <T> 业务数据类型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageWrapper<T> {

    /** 消息唯一 ID */
    @Builder.Default
    private String messageId = UUID.randomUUID().toString();

    /** Kafka 分区路由键（相同 Key 进入同一分区，保证消费顺序） */
    private String messageKey;

    /** 来源服务名称 */
    private String source;

    /** 消息创建时间 */
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    /** 消息类型（如 OrderMessage、PaymentMessage，用于消费者路由） */
    private String messageType;

    /** 业务负载 */
    private T payload;

    // ==================== 工厂方法 ====================

    /** 快速构造（自动生成 messageId + 时间戳） */
    public static <T> MessageWrapper<T> of(String messageType, T payload) {
        return MessageWrapper.<T>builder()
                .messageType(messageType)
                .payload(payload)
                .build();
    }

    /** 带分区键的构造（相同 Key 进入同一分区，保证顺序消费） */
    public static <T> MessageWrapper<T> of(String messageType, String messageKey, T payload) {
        return MessageWrapper.<T>builder()
                .messageType(messageType)
                .messageKey(messageKey)
                .payload(payload)
                .build();
    }

    /** 完整构造（含来源标识） */
    public static <T> MessageWrapper<T> of(String messageType, String messageKey, String source, T payload) {
        return MessageWrapper.<T>builder()
                .messageType(messageType)
                .messageKey(messageKey)
                .source(source)
                .payload(payload)
                .build();
    }
}
