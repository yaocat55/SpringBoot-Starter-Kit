package com.quick.springbootkafka.consumer;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 死信队列（DLT）消费者 —— 处理消费重试耗尽后被投递到死信 Topic 的消息。
 * <p>
 * 死信消息的特征：
 * <ul>
 *   <li>位于 {@code order-topic.DLT}（原 Topic 名 + ".DLT" 后缀）</li>
 *   <li>Header 中携带原始异常的堆栈信息（{@code x-original-topic}、{@code x-exception-message} 等）</li>
 *   <li>需要人工介入或异步补偿</li>
 * </ul>
 * <p>
 * 处理策略：
 * <ol>
 *   <li>记录日志并发出告警</li>
 *   <li>写入数据库的死信记录表，等待人工处理</li>
 *   <li>ACK 消费（不 ACK 会阻塞分区，后续死信消息无法消费）</li>
 * </ol>
 */
@Slf4j
@Component
public class DeadLetterListener {

    @KafkaListener(topics = "order-topic.DLT", groupId = "${spring.application.name}-dlt-group")
    public void onDeadLetter(ConsumerRecord<String, Object> record, Acknowledgment ack) {
        try {
            log.warn("===== 死信消息 =====");
            log.warn("原始 Topic: {}", new String(getHeader(record, KafkaHeaders.DLT_ORIGINAL_TOPIC), StandardCharsets.UTF_8));
            log.warn("原始 Partition: {}", getHeader(record, KafkaHeaders.DLT_ORIGINAL_PARTITION));
            log.warn("原始 Offset: {}", getHeader(record, KafkaHeaders.DLT_ORIGINAL_OFFSET));
            log.warn("异常信息: {}", new String(getHeader(record, KafkaHeaders.DLT_EXCEPTION_MESSAGE), StandardCharsets.UTF_8));
            log.warn("异常堆栈: {}", new String(getHeader(record, KafkaHeaders.DLT_EXCEPTION_STACKTRACE), StandardCharsets.UTF_8));
            log.warn("当前 DLT Topic: {}, Partition: {}, Offset: {}",
                    record.topic(), record.partition(), record.offset());

            // 实际项目：写入数据库死信表、发送钉钉/企微告警
            // deadLetterRepository.save(...);

        } catch (Exception e) {
            log.error("处理死信消息异常", e);
        } finally {
            // 死信消息也必须 ACK，避免阻塞分区
            ack.acknowledge();
        }
    }

    private byte[] getHeader(ConsumerRecord<String, Object> record, String key) {
        Header header = record.headers().lastHeader(key);
        return header != null ? header.value() : "N/A".getBytes(StandardCharsets.UTF_8);
    }
}
