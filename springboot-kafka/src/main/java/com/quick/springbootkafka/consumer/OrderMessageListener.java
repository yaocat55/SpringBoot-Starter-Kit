package com.quick.springbootkafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quick.springbootkafka.model.OrderMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.stereotype.Component;

/**
 * 订单消息消费者 —— 演示手动 ACK 和业务处理。
 * <p>
 * 核心机制：
 * <ul>
 *   <li><b>手动 ACK</b> —— 处理成功后调用 {@link Acknowledgment#acknowledge()} 提交 Offset</li>
 *   <li><b>异常重试</b> —— 消费失败抛异常，由 {@code DefaultErrorHandler} 自动重试</li>
 *   <li><b>死信</b> —— 重试用尽后自动投递到 {@code order-topic.DLT}</li>
 *   <li><b>分区路由</b> —— 生产端按 orderId 哈希，相同订单的消息由同一消费者处理</li>
 * </ul>
 * <p>
 * 手动 ACK 的三种情况：
 * <ol>
 *   <li>正常处理 → {@code ack.acknowledge()} 提交 Offset</li>
 *   <li>业务异常（如订单状态非法）→ 不抛异常，仅记日志，仍然 ACK（避免无限重试）</li>
 *   <li>系统异常（如 DB 连接失败）→ 抛 RuntimeException，触发 DefaultErrorHandler 重试</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderMessageListener {

    private final ObjectMapper objectMapper;

    /**
     * 消费订单 Topic 消息。
     * <p>
     * {@code groupId} 相同则属于同一消费者组，Topic 的分区在组内分配。
     * 如需多个消费者组各自独立消费（类似广播），使用不同的 groupId 即可。
     *
     * @param record Kafka 原生 ConsumerRecord（含 topic、partition、offset、key、value、headers）
     * @param ack    手动确认对象
     */
    @KafkaListener(topics = "order-topic", groupId = "${spring.application.name}-consumer-group")
    public void onMessage(ConsumerRecord<String, Object> record, Acknowledgment ack) {
        try {
            Object rawValue = record.value();
            log.info("收到消息: topic={}, partition={}, offset={}, key={}",
                    record.topic(), record.partition(), record.offset(), record.key());

            // 将 value 转换为 OrderMessage
            OrderMessage order = objectMapper.convertValue(rawValue, OrderMessage.class);

            // 按订单状态分发业务处理
            switch (order.getStatus()) {
                case "CREATED":
                    handleOrderCreated(order);
                    break;
                case "PAID":
                    handleOrderPaid(order);
                    break;
                case "DONE":
                    handleOrderDone(order);
                    break;
                default:
                    log.warn("未知订单状态: status={}, orderId={}", order.getStatus(), order.getOrderId());
            }

            // 处理成功 → 手动确认
            ack.acknowledge();
            log.debug("消息处理完成并确认: orderId={}, offset={}", order.getOrderId(), record.offset());

        } catch (IllegalArgumentException e) {
            // 业务异常：数据不合法，直接 ACK 避免死循环重试
            log.error("业务异常（跳过）: msg={}, offset={}", e.getMessage(), record.offset(), e);
            ack.acknowledge();
        } catch (Exception e) {
            // 系统异常：抛出让 DefaultErrorHandler 重试 → 最终进 DLT
            log.error("系统异常（将重试）: offset={}", record.offset(), e);
            throw new RuntimeException("消费失败，触发重试", e);
        }
    }

    private void handleOrderCreated(OrderMessage order) {
        log.info("[订单创建] orderId={}, orderNo={}, amount={}, userId={}",
                order.getOrderId(), order.getOrderNo(), order.getAmount(), order.getUserId());
        // 实际项目：写入数据库、发送通知等
    }

    private void handleOrderPaid(OrderMessage order) {
        log.info("[订单支付] orderId={}, orderNo={}, amount={}",
                order.getOrderId(), order.getOrderNo(), order.getAmount());
        // 实际项目：更新订单状态、触发发货流程
    }

    private void handleOrderDone(OrderMessage order) {
        log.info("[订单完成] orderId={}, orderNo={}", order.getOrderId(), order.getOrderNo());
        // 实际项目：归档、积分发放
    }
}
