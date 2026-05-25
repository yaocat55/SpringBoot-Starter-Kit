package com.quick.springbootkafka.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单消息模型 —— 演示用。
 * <p>
 * 包含订单生命周期中的核心字段：
 * <ul>
 *   <li>orderId —— 订单 ID，通常也作为 Kafka 分区键保证同一订单消息有序</li>
 *   <li>orderNo —— 订单号（业务流水号）</li>
 *   <li>status —— 订单状态（CREATED / PAID / SHIPPED / DONE / CANCELLED）</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderMessage {

    /** 订单 ID（业务主键，常用作分区键） */
    private Long orderId;

    /** 订单号（业务流水号） */
    private String orderNo;

    /** 用户 ID */
    private Long userId;

    /** 订单金额 */
    private BigDecimal amount;

    /** 订单状态 */
    private String status;

    /** 创建时间 */
    private LocalDateTime createTime;
}
