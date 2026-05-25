package com.quick.springbootkafka.consumer;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

/**
 * 延迟/定时消息消费者。
 * <p>
 * 注意：Kafka 没有内置延迟消息机制（不像 RabbitMQ 的 TTL+DLX 或 RocketMQ 的延迟等级），
 * 这里演示"应用层延迟"——在消费端做时间检查，未到执行时间的消息不处理并 nack 重新入队。
 * <p>
 * 生产环境推荐两种方案：
 * <ol>
 *   <li><b>外部定时调度</b> —— 用 XXL-Job / Quartz 定时扫表执行延迟任务</li>
 *   <li><b>用 RocketMQ 的延迟消息</b> —— RocketMQ 原生支持 18 个延迟等级（1s~2h）</li>
 *   <li><b>用 Redis ZSet 实现</b> —— score 存执行时间戳，定时轮询到期任务</li>
 * </ol>
 * <p>
 * 本示例仅演示 Kafka 消费延迟消息的基本模式（消息中 carry 一个 executeAt 字段）。
 */
@Slf4j
@Component
public class DelayMessageConsumer {

    @KafkaListener(topics = "delay-topic", groupId = "${spring.application.name}-delay-group")
    public void onDelayMessage(ConsumerRecord<String, Object> record, Acknowledgment ack) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) record.value();
            Object delaySeconds = payload.get("delaySeconds");

            long delay = delaySeconds != null ? ((Number) delaySeconds).longValue() : 0;

            // 从消息时间戳计算实际延迟
            long msgTimestamp = record.timestamp();
            long now = System.currentTimeMillis();
            long elapsed = now - msgTimestamp;
            long actualDelay = Duration.ofMillis(elapsed).getSeconds();

            log.info("收到延迟消息: topic={}, partition={}, offset={}, "
                            + "预期延迟={}s, 实际延迟={}s, 消息时间={}",
                    record.topic(), record.partition(), record.offset(), delay, actualDelay,
                    LocalDateTime.ofInstant(Instant.ofEpochMilli(msgTimestamp), ZoneId.systemDefault()));

            // 实际项目：执行延迟任务（如订单超时取消、会议提醒等）
            // if (actualDelay >= delay) { executeTask(payload); }

            ack.acknowledge();
        } catch (Exception e) {
            log.error("延迟消息处理异常: offset={}", record.offset(), e);
            ack.acknowledge();
        }
    }
}
