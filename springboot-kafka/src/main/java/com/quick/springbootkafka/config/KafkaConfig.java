package com.quick.springbootkafka.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Kafka 核心配置 —— Producer / Consumer / Admin / ErrorHandler。
 * <p>
 * Spring Boot 自动配置了大部分 Kafka 基础设施，这里做定制化增强：
 * <ul>
 *   <li>自定义 ObjectMapper（支持 Java 8 时间序列化）</li>
 *   <li>KafkaTemplate（同步/异步发送、事务支持）</li>
 *   <li>KafkaListenerContainerFactory（手动 ACK、错误处理、死信）</li>
 *   <li>Topic 自动创建（KafkaAdmin + NewTopic）</li>
 * </ul>
 * <p>
 * 与 RabbitMQ / RocketMQ 的重要区别：
 * <ul>
 *   <li>Kafka 没有 Exchange，消息按 Key 哈希分区（或指定分区号）</li>
 *   <li>Kafka 消费者通过 {@code @KafkaListener} 注解，指定 topic 和 groupId</li>
 *   <li>Kafka 无内置延迟消息，需通过外部组件（如 RocketMQ 定时消息）或手动重试实现</li>
 *   <li>Kafka 支持事务（exactly-once），通过 {@code KafkaTemplate.executeInTransaction()} 使用</li>
 *   <li>Kafka 的消息是持久化的，即使消费者挂了也不会丢消息</li>
 * </ul>
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class KafkaConfig {

    private final KafkaProperties kafkaProperties;

    // ==================== ObjectMapper ====================

    /**
     * 注册带 JavaTimeModule 的 ObjectMapper，供 Kafka JSON 序列化使用。
     * <p>
     * {@link JsonSerializer} 会自动发现此 Bean 并使用它做 JSON 序列化，
     * 无需手动 setObjectMapper（Spring Kafka 3.x 自动注入）。
     */
    @Bean
    public ObjectMapper kafkaObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }

    // ==================== Producer ====================

    /**
     * KafkaTemplate —— 发送消息的核心模板。
     * <p>
     * Spring Boot 自动配置了默认的 KafkaTemplate，但此处显式注册
     * 以便在 ProducerFactory 上设置自定义序列化器。
     */
    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    // ==================== Consumer ====================

    /**
     * KafkaListenerContainerFactory —— 消费者监听器容器工厂。
     * <p>
     * 配置要点：
     * <ul>
     *   <li>手动 ACK（ackMode=MANUAL_IMMEDIATE，单条处理完立即确认）</li>
     *   <li>死信队列：3 次重试后投递到 DLT</li>
     *   <li>并发消费（通过 application.yml 中的 concurrency 控制）</li>
     * </ul>
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory,
            KafkaTemplate<String, Object> kafkaTemplate) {

        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        // 手动 ACK：每条消息处理完后立即提交 Offset
        factory.getContainerProperties().setAckMode(
                org.springframework.kafka.listener.ContainerProperties.AckMode.MANUAL);
        // 并发数
        factory.setConcurrency(4);
        // 设置通用错误处理器（重试 + 死信）
        factory.setCommonErrorHandler(kafkaErrorHandler(kafkaTemplate));
        return factory;
    }

    /**
     * Kafka 错误处理器 —— 重试 3 次后投递到死信 Topic。
     * <p>
     * 策略：
     * <ol>
     *   <li>消费失败 → 重试（每次间隔 2 秒）</li>
     *   <li>重试 3 次仍失败 → 自动投递到 {原 topic}.DLT（死信队列）</li>
     *   <li>死信消息会携带原始异常和来源 Topic 信息到 Header 中</li>
     * </ol>
     * <p>
     * 注意：死信 Topic（如 {@code order-topic.DLT}）需提前创建或由 NewTopic Bean 自动创建。
     */
    @Bean
    public CommonErrorHandler kafkaErrorHandler(KafkaTemplate<String, Object> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate);
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer,
                new FixedBackOff(2000L, 3L));   // 间隔 2s，最多 3 次重试
        // 不重试的异常类型（如反序列化失败、参数校验失败）
        errorHandler.addNotRetryableExceptions(
                IllegalArgumentException.class,
                com.fasterxml.jackson.core.JsonProcessingException.class
        );
        return errorHandler;
    }

    // ==================== Topic 自动创建 ====================

    /**
     * 订单 Topic —— 3 分区 2 副本。
     * <p>
     * 分区策略：按 orderId 哈希，同一订单的消息有序。
     */
    @Bean
    public NewTopic orderTopic() {
        return TopicBuilder.name("order-topic")
                .partitions(3)
                .replicas(1)
                .config(TopicConfig.RETENTION_MS_CONFIG, "604800000")  // 保留 7 天
                .build();
    }

    /** 死信 Topic */
    @Bean
    public NewTopic deadLetterTopic() {
        return TopicBuilder.name("order-topic.DLT")
                .partitions(1)
                .replicas(1)
                .config(TopicConfig.RETENTION_MS_CONFIG, "1209600000") // 保留 14 天
                .build();
    }

    /** 延迟 Topic */
    @Bean
    public NewTopic delayTopic() {
        return TopicBuilder.name("delay-topic")
                .partitions(1)
                .replicas(1)
                .build();
    }
}
