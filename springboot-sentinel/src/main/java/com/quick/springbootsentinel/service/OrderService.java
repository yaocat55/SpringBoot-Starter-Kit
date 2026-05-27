package com.quick.springbootsentinel.service;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.quick.springbootsentinel.handler.CustomBlockHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 订单 Service —— 演示 @SentinelResource 在 Service 层的用法。
 * <p>
 * Service 层加 Sentinel 的优势：
 * <ul>
 *   <li>不限调用入口（Controller / 定时任务 / MQ 消费者 都能保护）</li>
 *   <li>fallback 可以更贴近业务（比如返回缓存、降级到备选方案）</li>
 *   <li>与 Controller 层的流控形成双层保护</li>
 * </ul>
 */
@Slf4j
@Service
public class OrderService {

    /**
     * 下单接口 —— 同时配置 blockHandler 和 fallback。
     * <p>
     * blockHandler → 被 Sentinel 规则拦截时走这里（限流/熔断）
     * fallback     → 业务代码抛异常时走这里（业务降级）
     * <p>
     * 新手最常踩的坑：fallback 方法签名必须完全一致参数 + Throwable；
     * blockHandler 签名必须完全一致参数 + BlockException。
     */
    @SentinelResource(value = "createOrder",
            blockHandler = "createOrderBlock",
            fallback = "createOrderFallback")
    public Map<String, Object> createOrder(String userId, String productId) {
        log.info("[下单] userId={}, productId={}", userId, productId);

        // 模拟业务处理耗时
        sleep(50);

        // 随机模拟库存不足
        if (ThreadLocalRandom.current().nextDouble() < 0.2) {
            throw new RuntimeException("库存不足");
        }

        long orderId = ThreadLocalRandom.current().nextLong(100000, 999999);
        return Map.of("success", true, "orderId", orderId,
                "userId", userId, "productId", productId,
                "status", "已下单");
    }

    /** 下单 - 限流/熔断 block handler */
    public Map<String, Object> createOrderBlock(String userId, String productId,
                                                 com.alibaba.csp.sentinel.slots.block.BlockException e) {
        log.warn("[下单-限流] userId={}, productId={}, rule={}", userId, productId, e.getRule().getResource());
        return Map.of("success", false, "msg", "下单太火爆，请稍后重试",
                "blocked", true, "rule", e.getRule().getResource());
    }

    /** 下单 - 业务异常 fallback */
    public Map<String, Object> createOrderFallback(String userId, String productId, Throwable t) {
        log.error("[下单-降级] userId={}, error={}", userId, t.getMessage());
        return Map.of("success", false, "msg", "下单失败：" + t.getMessage(),
                "fallback", true);
    }

    private void sleep(long ms) {
        try { TimeUnit.MILLISECONDS.sleep(ms); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
