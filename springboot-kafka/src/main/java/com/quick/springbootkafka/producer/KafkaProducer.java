package com.quick.springbootkafka.producer;

import com.quick.springbootkafka.model.MessageWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Kafka 消息生产者 —— 封装全部发送方式。
 * <p>
 * Kafka 发送模式对比：
 * <ul>
 *   <li><b>同步发送</b> —— 通过 {@code get()} 阻塞等待 Broker 确认，可靠性最高</li>
 *   <li><b>异步发送</b> —— 不阻塞线程，通过 {@code CompletableFuture} 回调处理结果</li>
 *   <li><b>带 Key 发送</b> —— 相同 Key 的消息路由到同一分区，保证有序</li>
 *   <li><b>指定分区/时间戳/Header</b> —— 通过 {@link ProducerRecord} 精确控制</li>
 *   <li><b>批量发送</b> —— 多条消息合并，减少网络开销</li>
 *   <li><b>事务发送</b> —— 保证 exactly-once，多分区原子写入</li>
 * </ul>
 * <p>
 * 核心 API：{@link KafkaTemplate#send(String, Object)} 和 {@link KafkaTemplate#send(String, Object, Object)}。
 * Topic 通过 {@link com.quick.springbootkafka.config.KafkaConfig} 中的 {@code NewTopic} Bean 自动创建。
 * <p>
 * 与 RabbitMQ 的重要区别：Kafka 无 Exchange，消息直接发到 Topic，
 * 分区由 Key 的哈希值决定（{@code hash(key) % numPartitions}）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    // ==================== 同步发送（等待结果） ====================

    /**
     * 同步发送 —— 阻塞等待 Broker 确认后返回。
     * <pre>
     * SendResult result = producer.syncSend("order-topic", orderMsg);
     * log.info("发送成功: offset={}, partition={}", result.getRecordMetadata().offset(),
     *          result.getRecordMetadata().partition());
     * </pre>
     */
    public SendResult<String, Object> syncSend(String topic, Object payload) {
        try {
            SendResult<String, Object> result = kafkaTemplate.send(topic, payload)
                    .get(10, TimeUnit.SECONDS);
            log.debug("同步发送成功: topic={}, partition={}, offset={}",
                    topic, result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());
            return result;
        } catch (Exception e) {
            log.error("同步发送失败: topic={}", topic, e);
            throw new RuntimeException("Kafka 同步发送失败", e);
        }
    }

    /** 同步发送（带 Key，相同 Key 消息进入同一分区保证有序） */
    public SendResult<String, Object> syncSend(String topic, String key, Object payload) {
        try {
            SendResult<String, Object> result = kafkaTemplate.send(topic, key, payload)
                    .get(10, TimeUnit.SECONDS);
            log.debug("同步发送成功: topic={}, key={}, partition={}, offset={}",
                    topic, key, result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());
            return result;
        } catch (Exception e) {
            log.error("同步发送失败: topic={}, key={}", topic, key, e);
            throw new RuntimeException("Kafka 同步发送失败", e);
        }
    }

    /** 同步发送（使用 MessageWrapper，自动设置 Header） */
    public SendResult<String, Object> syncSend(String topic, MessageWrapper<?> wrapper) {
        String key = wrapper.getMessageKey() != null ? wrapper.getMessageKey() : wrapper.getMessageId();
        return syncSend(topic, key, wrapper);
    }

    // ==================== 异步发送（非阻塞，回调处理） ====================

    /**
     * 异步发送 —— 不阻塞调用线程，通过 {@link CompletableFuture} 处理结果。
     * <pre>
     * producer.asyncSend("order-topic", orderMsg,
     *     result -> log.info("发送成功: offset={}", result.getRecordMetadata().offset()),
     *     ex -> log.error("发送失败", ex));
     * </pre>
     */
    public void asyncSend(String topic, Object payload,
                          java.util.function.Consumer<SendResult<String, Object>> onSuccess,
                          java.util.function.Consumer<Throwable> onFailure) {
        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(topic, payload);
        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.debug("异步发送成功: topic={}, partition={}, offset={}",
                        topic, result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
                if (onSuccess != null) onSuccess.accept(result);
            } else {
                log.error("异步发送失败: topic={}", topic, ex);
                if (onFailure != null) onFailure.accept(ex);
            }
        });
    }

    /** 异步发送（带 Key） */
    public void asyncSend(String topic, String key, Object payload,
                          java.util.function.Consumer<SendResult<String, Object>> onSuccess,
                          java.util.function.Consumer<Throwable> onFailure) {
        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(topic, key, payload);
        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.debug("异步发送成功: topic={}, key={}, partition={}, offset={}",
                        topic, key, result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
                if (onSuccess != null) onSuccess.accept(result);
            } else {
                log.error("异步发送失败: topic={}, key={}", topic, key, ex);
                if (onFailure != null) onFailure.accept(ex);
            }
        });
    }

    // ==================== 带 Header 发送 ====================

    /**
     * 发送带自定义 Header 的消息。
     * <p>
     * Kafka Header 可用于传递元数据（如 traceId、消息类型、来源服务等），
     * 消费端通过 {@code ConsumerRecord.headers()} 读取。
     * <pre>
     * producer.syncSendWithHeaders("order-topic", key, payload,
     *     Map.of("traceId", "abc123", "source", "order-service"));
     * </pre>
     */
    public SendResult<String, Object> syncSendWithHeaders(String topic, String key, Object payload,
                                                           java.util.Map<String, String> headers) {
        try {
            List<Header> kafkaHeaders = new ArrayList<>();
            if (headers != null) {
                headers.forEach((k, v) -> kafkaHeaders.add(
                        new RecordHeader(k, v.getBytes(StandardCharsets.UTF_8))));
            }
            ProducerRecord<String, Object> record = new ProducerRecord<>(
                    topic, null, System.currentTimeMillis(), key, payload, kafkaHeaders);
            SendResult<String, Object> result = kafkaTemplate.send(record).get(10, TimeUnit.SECONDS);
            log.debug("同步发送成功(带Header): topic={}, key={}, partition={}, headerCount={}",
                    topic, key, result.getRecordMetadata().partition(), headers != null ? headers.size() : 0);
            return result;
        } catch (Exception e) {
            log.error("同步发送失败(带Header): topic={}, key={}", topic, key, e);
            throw new RuntimeException("Kafka 同步发送失败", e);
        }
    }

    // ==================== 指定分区发送 ====================

    /**
     * 发送到指定分区 —— 绕过 Key 哈希路由，直接指定目标分区号。
     * <p>适用场景：需要手动控制分区分配的场合（如数据迁移、分桶写入）。
     * <pre>
     * // 强制发往分区 0
     * producer.syncSendToPartition("order-topic", 0, key, payload);
     * </pre>
     */
    public SendResult<String, Object> syncSendToPartition(String topic, int partition, String key, Object payload) {
        try {
            SendResult<String, Object> result = kafkaTemplate.send(topic, partition, key, payload)
                    .get(10, TimeUnit.SECONDS);
            log.debug("同步发送成功(指定分区): topic={}, partition={}, offset={}",
                    topic, result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());
            return result;
        } catch (Exception e) {
            log.error("同步发送失败(指定分区): topic={}, partition={}", topic, partition, e);
            throw new RuntimeException("Kafka 同步发送失败", e);
        }
    }

    // ==================== 批量发送 ====================

    /**
     * 批量同步发送 —— 多条消息合并为一次网络请求。
     * <p>注意：单条消息大小不能超过 {@code max.request.size}（默认 1MB），
     * 批次总大小受 {@code batch.size} 和 {@code buffer.memory} 控制。
     */
    public void syncSendBatch(String topic, List<MessageWrapper<?>> wrappers) {
        for (MessageWrapper<?> wrapper : wrappers) {
            String key = wrapper.getMessageKey() != null ? wrapper.getMessageKey() : wrapper.getMessageId();
            try {
                kafkaTemplate.send(topic, key, wrapper).get(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.error("批量发送失败: topic={}, messageId={}", topic, wrapper.getMessageId(), e);
                throw new RuntimeException("Kafka 批量发送失败", e);
            }
        }
        log.debug("批量发送完成: topic={}, count={}", topic, wrappers.size());
    }

    // ==================== 事务发送（Exactly-Once） ====================

    /**
     * 事务发送 —— 保证多分区写入的原子性（exactly-once）。
     * <p>
     * 使用前提：
     * <ul>
     *   <li>Producer 配置 {@code transaction-id-prefix}（每个事务生产者唯一）</li>
     *   <li>Consumer 设置 {@code isolation.level=read_committed}（只读已提交消息）</li>
     * </ul>
     * <p>
     * 典型场景：订单创建 + 库存扣减 + 积分变更 三件事原子写入不同 Topic。
     * <pre>
     * producer.executeInTransaction(template -> {
     *     template.send("order-topic", orderMsg);
     *     template.send("inventory-topic", inventoryMsg);
     *     template.send("points-topic", pointsMsg);
     *     return true; // 返回 true 提交，抛异常则回滚
     * });
     * </pre>
     */
    public void executeInTransaction(java.util.function.Function<org.springframework.kafka.core.KafkaOperations<String, Object>, Boolean> action) {
        kafkaTemplate.executeInTransaction(t -> {
            Boolean result = action.apply(t);
            if (Boolean.FALSE.equals(result)) {
                throw new RuntimeException("事务内操作返回 false，触发回滚");
            }
            return result;
        });
        log.debug("事务提交成功");
    }

    // ==================== 通用便捷方法 ====================

    /**
     * 通用发送（同步，最常用的方法）。
     * <pre>
     * producer.send("order-topic", orderId.toString(), orderMsg);
     * </pre>
     */
    public SendResult<String, Object> send(String topic, String key, Object payload) {
        return syncSend(topic, key, payload);
    }

    /** 通用发送（不带 Key，随机分区） */
    public SendResult<String, Object> send(String topic, Object payload) {
        return syncSend(topic, payload);
    }
}
