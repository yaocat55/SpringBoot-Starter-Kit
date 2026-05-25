package com.quick.springbootrocketmq.producer;

import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.LocalTransactionState;
import org.apache.rocketmq.client.producer.TransactionListener;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * RocketMQ 事务消息监听器 —— 实现本地事务 + 事务回查。
 * <p>
 * 二阶段提交流程：
 * <ol>
 *   <li>Producer 发送 Half-Message（消费者不可见）</li>
 *   <li>{@link #executeLocalTransaction(Message, Object)} 执行本地事务</li>
 *   <li>成功 → 返回 COMMIT_MESSAGE → 消费者可见</li>
 *   <li>失败 → 返回 ROLLBACK_MESSAGE → 消息删除</li>
 *   <li>Broker 长时间没收到确认 → 回调 {@link #checkLocalTransaction(MessageExt)} 做事务回查</li>
 * </ol>
 * <p>
 * 实际项目建议：
 * <ul>
 *   <li>本地事务状态持久化到数据库，回查时查数据库确认</li>
 *   <li>不要在此监听器中执行耗时操作</li>
 * </ul>
 */
@Slf4j
@Component("transactionListener")
public class SimpleTransactionListener implements TransactionListener {

    /** 模拟本地事务状态存储（实际项目用数据库） */
    private final ConcurrentMap<String, LocalTransactionState> transactionCache = new ConcurrentHashMap<>();

    @Override
    public LocalTransactionState executeLocalTransaction(Message msg, Object arg) {
        String transactionId = msg.getTransactionId();
        String body = new String(msg.getBody(), StandardCharsets.UTF_8);

        log.info("[事务消息] 开始执行本地事务: transactionId={}, body={}, arg={}",
                transactionId, body, arg);

        try {
            // ===== 模拟本地事务（实际项目：写数据库、调 RPC 等） =====
            // orderService.createOrder(order);
            // paymentService.deduct(account);

            // 记录事务状态（用于回查）
            transactionCache.put(transactionId, LocalTransactionState.COMMIT_MESSAGE);
            log.info("[事务消息] 本地事务执行成功，提交: transactionId={}", transactionId);
            return LocalTransactionState.COMMIT_MESSAGE;

        } catch (Exception e) {
            log.error("[事务消息] 本地事务执行失败，回滚: transactionId={}", transactionId, e);
            transactionCache.put(transactionId, LocalTransactionState.ROLLBACK_MESSAGE);
            return LocalTransactionState.ROLLBACK_MESSAGE;
        }
    }

    @Override
    public LocalTransactionState checkLocalTransaction(MessageExt msg) {
        String transactionId = msg.getTransactionId();

        log.info("[事务消息] Broker 回查事务状态: transactionId={}", transactionId);

        // 从缓存/数据库查询事务状态
        LocalTransactionState state = transactionCache.get(transactionId);
        if (state != null) {
            log.info("[事务消息] 回查结果: transactionId={}, state={}", transactionId, state);
            // 注意：返回 COMMIT_MESSAGE 后缓存可清除
            transactionCache.remove(transactionId);
            return state;
        }

        // 查不到状态 → 可能是未知异常，返回 UNKNOW 等待下次回查（最多 15 次）
        log.warn("[事务消息] 回查无结果，返回 UNKNOW: transactionId={}", transactionId);
        return LocalTransactionState.UNKNOW;
    }
}
