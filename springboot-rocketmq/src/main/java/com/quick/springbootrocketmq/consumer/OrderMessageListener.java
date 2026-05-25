package com.quick.springbootrocketmq.consumer;

import com.quick.springbootrocketmq.model.OrderMessage;
import com.quick.springbootrocketmq.model.MessageWrapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 订单消息消费者 —— 演示集群消费 + 顺序消费。
 * <p>
 * 关键注解参数说明：
 * <ul>
 *   <li>{@code topic} —— 订阅的 Topic</li>
 *   <li>{@code consumerGroup} —— 消费者组名（同组负载均衡，不同组各自独立）</li>
 *   <li>{@code selectorExpression} —— Tag 过滤表达式（* 表示全部，多个用 || 分隔，如 "tag1||tag2"）</li>
 *   <li>{@code consumeMode} —— {@link ConsumeMode#ORDERLY} 顺序消费 | {@link ConsumeMode#CONCURRENTLY} 并发消费</li>
 *   <li>{@code messageModel} —— {@link MessageModel#CLUSTERING} 集群 | {@link MessageModel#BROADCASTING} 广播</li>
 *   <li>{@code maxReconsumeTimes} —— 消费失败最大重试次数，达到后进死信 Topic（%DLQ%前缀）</li>
 * </ul>
 * <p>
 * 集群消费 vs 广播消费：
 * <ul>
 *   <li><b>集群消费（默认）</b>：同组消费者分摊消息，一条消息只会被组内一个消费者处理</li>
 *   <li><b>广播消费</b>：同组所有消费者都会收到同样的消息</li>
 * </ul>
 */
@Slf4j
@Component
@RocketMQMessageListener(
        topic = "${rocketmq-config.order-topic:order-topic}",
        consumerGroup = "${rocketmq-config.order-topic:order-topic}-consumer",
        selectorExpression = "order-created||order-paid||order-cancelled",
        consumeMode = ConsumeMode.CONCURRENTLY,   // 并发消费
        messageModel = MessageModel.CLUSTERING     // 集群消费
)
public class OrderMessageListener implements RocketMQListener<MessageWrapper<OrderMessage>> {

    @Override
    public void onMessage(MessageWrapper<OrderMessage> wrapper) {
        String tag = wrapper.getMessageType();
        OrderMessage order = wrapper.getPayload();
        String messageKey = wrapper.getMessageKey();

        log.info("[订单消费者] 收到消息: tag={}, messageKey={}, orderId={}, orderNo={}, amount={}",
                tag, messageKey, order.getOrderId(), order.getOrderNo(), order.getAmount());

        // 按 Tag 分流处理
        switch (tag) {
            case "order-created" -> handleOrderCreated(order, wrapper.getMessageId());
            case "order-paid"    -> handleOrderPaid(order, wrapper.getMessageId());
            case "order-cancelled" -> handleOrderCancelled(order, wrapper.getMessageId());
            default -> log.warn("[订单消费者] 未知 Tag: {}", tag);
        }
    }

    private void handleOrderCreated(OrderMessage order, String messageId) {
        // 扣库存、发优惠券等
        log.info("[订单创建] 处理订单: orderId={}", order.getOrderId());
        // throw new RuntimeException("模拟失败");  // 测试重试 → 死信
    }

    private void handleOrderPaid(OrderMessage order, String messageId) {
        // 更新订单状态、通知发货系统等
        log.info("[订单支付] 处理支付: orderId={}, amount={}", order.getOrderId(), order.getAmount());
    }

    private void handleOrderCancelled(OrderMessage order, String messageId) {
        // 恢复库存、退款等
        log.info("[订单取消] 处理取消: orderId={}", order.getOrderId());
    }
}
