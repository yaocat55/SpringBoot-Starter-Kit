package com.quick.springbootemqx.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 消息信封 —— 统一的消息包装格式。
 * <p>
 * 给业务消息套一层标准信封，方便下游按 messageType 路由、按 messageKey 做业务关联。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmqxMessageWrapper<T> {

    /** 消息唯一 ID */
    @Builder.Default
    private String messageId = UUID.randomUUID().toString().replace("-", "");

    /** 业务标识（如设备 ID、订单号），用于分区/分片 */
    private String messageKey;

    /** 消息来源 */
    @Builder.Default
    private String source = "springboot-emqx";

    /** 消息时间戳 */
    @Builder.Default
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime timestamp = LocalDateTime.now();

    /** 消息类型标识（如 DEVICE_TELEMETRY, ORDER_CREATED） */
    private String messageType;

    /** 业务数据体 */
    private T payload;

    // ---- 工厂方法 ----

    public static <T> EmqxMessageWrapper<T> of(String messageType, String messageKey, T payload) {
        return EmqxMessageWrapper.<T>builder()
                .messageType(messageType)
                .messageKey(messageKey)
                .payload(payload)
                .build();
    }
}
