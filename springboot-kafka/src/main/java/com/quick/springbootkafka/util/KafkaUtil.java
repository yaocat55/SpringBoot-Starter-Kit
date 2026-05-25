package com.quick.springbootkafka.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.TopicPartitionInfo;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

/**
 * Kafka 管理工具类 —— Topic 管理 / 消费者组查询 / Offset 操作。
 * <p>
 * 基于 {@link AdminClient}，封装常用管理操作：
 * <ul>
 *   <li>Topic 的增删改查</li>
 *   <li>消费者组 Offset 查询与重置</li>
 *   <li>分区信息查询</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaUtil {

    private final KafkaAdmin kafkaAdmin;

    // ==================== Topic 管理 ====================

    /**
     * 查询所有 Topic 列表。
     */
    public Set<String> listTopics() {
        try (AdminClient client = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            return client.listTopics().names().get();
        } catch (Exception e) {
            log.error("查询 Topic 列表失败", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * 创建 Topic。
     *
     * @param topic        Topic 名称
     * @param numPartitions 分区数（建议 >= 消费者线程数）
     * @param replicationFactor 副本数（不能超过 Broker 数量）
     */
    public void createTopic(String topic, int numPartitions, short replicationFactor) {
        try (AdminClient client = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            NewTopic newTopic = new NewTopic(topic, numPartitions, replicationFactor);
            CreateTopicsResult result = client.createTopics(Collections.singleton(newTopic));
            result.all().get();
            log.info("Topic 创建成功: topic={}, partitions={}, replicas={}",
                    topic, numPartitions, replicationFactor);
        } catch (Exception e) {
            log.error("创建 Topic 失败: topic={}", topic, e);
            throw new RuntimeException(e);
        }
    }

    /**
     * 删除 Topic（不可逆操作，谨慎使用）。
     */
    public void deleteTopic(String topic) {
        try (AdminClient client = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            DeleteTopicsResult result = client.deleteTopics(Collections.singleton(topic));
            result.all().get();
            log.info("Topic 删除成功: topic={}", topic);
        } catch (Exception e) {
            log.error("删除 Topic 失败: topic={}", topic, e);
            throw new RuntimeException(e);
        }
    }

    /**
     * 查询 Topic 的详细信息（分区、副本、ISR）。
     */
    public Map<String, Object> describeTopic(String topic) {
        try (AdminClient client = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            DescribeTopicsResult result = client.describeTopics(Collections.singleton(topic));
            TopicDescription desc = result.topicNameValues().get(topic).get();

            Map<String, Object> info = new LinkedHashMap<>();
            info.put("name", desc.name());
            info.put("internal", desc.isInternal());
            info.put("partitionCount", desc.partitions().size());

            List<Map<String, Object>> partitions = new ArrayList<>();
            for (TopicPartitionInfo p : desc.partitions()) {
                Map<String, Object> pInfo = new LinkedHashMap<>();
                pInfo.put("partition", p.partition());
                pInfo.put("leader", p.leader() != null ? p.leader().host() + ":" + p.leader().port() : "N/A");
                pInfo.put("replicas", p.replicas().stream()
                        .map(n -> n.host() + ":" + n.port()).collect(Collectors.toList()));
                pInfo.put("isr", p.isr().stream()
                        .map(n -> n.host() + ":" + n.port()).collect(Collectors.toList()));
                partitions.add(pInfo);
            }
            info.put("partitions", partitions);
            return info;
        } catch (Exception e) {
            log.error("查询 Topic 详情失败: topic={}", topic, e);
            throw new RuntimeException(e);
        }
    }

    /**
     * 查询 Topic 的分区数。
     */
    public int getPartitionCount(String topic) {
        try (AdminClient client = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            DescribeTopicsResult result = client.describeTopics(Collections.singleton(topic));
            TopicDescription desc = result.topicNameValues().get(topic).get();
            return desc.partitions().size();
        } catch (Exception e) {
            log.error("查询分区数失败: topic={}", topic, e);
            throw new RuntimeException(e);
        }
    }

    // ==================== 消费者组管理 ====================

    /**
     * 查询所有消费者组。
     */
    public Set<String> listConsumerGroups() {
        try (AdminClient client = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            return client.listConsumerGroups().all().get().stream()
                    .map(ConsumerGroupListing::groupId)
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            log.error("查询消费者组列表失败", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * 查询消费者组的 Offset 信息。
     *
     * @param groupId 消费者组 ID
     * @return Map，key 为 "topic-partition"，value 包含 currentOffset 和 endOffset
     */
    public Map<String, Map<String, Long>> getConsumerGroupOffsets(String groupId) {
        try (AdminClient client = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            // 1. 获取消费者组的已提交 Offset
            ListConsumerGroupOffsetsResult offsetResult = client.listConsumerGroupOffsets(groupId);
            Map<TopicPartition, OffsetAndMetadata> committedOffsets =
                    offsetResult.partitionsToOffsetAndMetadata().get();

            // 2. 获取各分区的 LEO（Log End Offset）
            Map<TopicPartition, Long> endOffsets = new HashMap<>();
            if (!committedOffsets.isEmpty()) {
                ListOffsetsResult listOffsetsResult = client.listOffsets(
                        committedOffsets.keySet().stream().collect(Collectors.toMap(
                                tp -> tp,
                                tp -> OffsetSpec.latest()
                        ))
                );
                for (Map.Entry<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> entry :
                        listOffsetsResult.all().get().entrySet()) {
                    endOffsets.put(entry.getKey(), entry.getValue().offset());
                }
            }

            // 3. 组装结果
            Map<String, Map<String, Long>> result = new LinkedHashMap<>();
            for (Map.Entry<TopicPartition, OffsetAndMetadata> entry : committedOffsets.entrySet()) {
                TopicPartition tp = entry.getKey();
                String key = tp.topic() + "-" + tp.partition();
                Map<String, Long> offsets = new LinkedHashMap<>();
                offsets.put("committedOffset", entry.getValue().offset());
                offsets.put("endOffset", endOffsets.getOrDefault(tp, -1L));
                offsets.put("lag", offsets.get("endOffset") - offsets.get("committedOffset"));
                result.put(key, offsets);
            }
            return result;
        } catch (Exception e) {
            log.error("查询消费者组 Offset 失败: groupId={}", groupId, e);
            throw new RuntimeException(e);
        }
    }

    // ==================== 消息查询 ====================

    /**
     * 查询指定分区的起始和结束 Offset。
     *
     * @param topic     主题名
     * @param partition 分区号
     * @return [beginningOffset, endOffset]
     */
    public Map<String, Long> getPartitionOffsets(String topic, int partition) {
        try (AdminClient client = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            TopicPartition tp = new TopicPartition(topic, partition);

            Map<TopicPartition, OffsetSpec> beginRequest = Collections.singletonMap(tp, OffsetSpec.earliest());
            Map<TopicPartition, OffsetSpec> endRequest = Collections.singletonMap(tp, OffsetSpec.latest());

            long beginning = client.listOffsets(beginRequest).all().get().get(tp).offset();
            long end = client.listOffsets(endRequest).all().get().get(tp).offset();

            Map<String, Long> result = new LinkedHashMap<>();
            result.put("beginningOffset", beginning);
            result.put("endOffset", end);
            result.put("totalMessages", end - beginning);
            return result;
        } catch (Exception e) {
            log.error("查询分区 Offset 失败: topic={}, partition={}", topic, partition, e);
            throw new RuntimeException(e);
        }
    }
}
