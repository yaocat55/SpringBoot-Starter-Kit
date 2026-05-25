package com.quick.springrabbitmq.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 统一消息包装体 —— 所有发送到 RabbitMQ 的消息都套此结构。
 * <p>
 * 标准化消息格式的好处：
 * <ul>
 *   <li>统一携带 messageId，便于链路追踪和去重</li>
 *   <li>统一携带 timestamp，方便排查延迟</li>
 *   <li>统一携带 source，标识消息来源（哪个服务发出的）</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageWrapper<T> implements Serializable {

    /** 消息唯一 ID（由发送方生成，可用于幂等去重） */
    @Builder.Default
    private String messageId = UUID.randomUUID().toString().replace("-", "");

    /** 消息来源服务名 */
    @Builder.Default
    private String source = "spring-rabbitmq";

    /** 消息产生时间 */
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    /** 消息类型（如 "ORDER_CREATED", "USER_REGISTERED"），便于消费方路由 */
    private String messageType;

    /** 消息体（泛型，实际业务数据） */
    private T payload;

    /** 创建快捷构建 */
    public static <T> MessageWrapper<T> of(String messageType, T payload) {
        return MessageWrapper.<T>builder()
                .messageType(messageType)
                .payload(payload)
                .build();
    }

    /** 创建快捷构建（带自定义 source） */
    public static <T> MessageWrapper<T> of(String source, String messageType, T payload) {
        return MessageWrapper.<T>builder()
                .source(source)
                .messageType(messageType)
                .payload(payload)
                .build();
    }
}
