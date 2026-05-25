package com.quick.springrabbitmq.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单消息体 —— 演示用业务消息。
 * <p>
 * 实际项目中的业务消息 POJO 也照此模式定义，放入 {@link MessageWrapper#payload} 中发送。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderMessage implements Serializable {

    /** 订单 ID */
    private Long orderId;

    /** 订单编号 */
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
