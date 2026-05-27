package com.quick.springbootsentinel.service;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 模拟外部服务调用 —— 演示 Sentinel 保护远程调用的场景。
 * <p>
 * 实际项目中，这里替换为 Feign / RestTemplate / gRPC 调用。
 * 用 @SentinelResource + fallback 保护每一个外部依赖。
 */
@Slf4j
@Service
public class ExternalService {

    /**
     * 模拟调用"用户服务"。
     * <p>
     * 30% 概率超时或失败，fallback 返回降级数据。
     */
    @SentinelResource(value = "fetchUserInfo",
            fallback = "fetchUserInfoFallback")
    public Map<String, Object> fetchUserInfo(String userId) {
        double r = ThreadLocalRandom.current().nextDouble();
        if (r < 0.3) {
            sleep(3000);  // 模拟超时
            throw new RuntimeException("用户服务调用超时");
        }
        if (r < 0.5) {
            throw new RuntimeException("用户服务返回 500");
        }
        return Map.of("userId", userId, "name", "张三-实时", "level", "VIP");
    }

    public Map<String, Object> fetchUserInfoFallback(String userId, Throwable t) {
        log.warn("[外部服务降级] userId={}, error={}", userId, t.getMessage());
        return Map.of("userId", userId, "name", "张三-缓存", "level", "VIP",
                "source", "降级缓存",
                "tip", "用户服务暂时不可用，返回上次缓存数据");
    }

    /**
     * 模拟调用"库存服务"。
     */
    @SentinelResource(value = "checkStock",
            fallback = "checkStockFallback")
    public Map<String, Object> checkStock(String skuId) {
        if (ThreadLocalRandom.current().nextDouble() < 0.4) {
            throw new RuntimeException("库存服务不可用");
        }
        int stock = ThreadLocalRandom.current().nextInt(1, 100);
        return Map.of("skuId", skuId, "stock", stock, "available", stock > 0,
                "source", "实时查询");
    }

    public Map<String, Object> checkStockFallback(String skuId, Throwable t) {
        log.warn("[库存服务降级] skuId={}, error={}", skuId, t.getMessage());
        // 库存查询降级：保守返回有货，避免因查询失败阻塞下单
        // 真实项目这里应该返回上一次缓存值或阻塞下单
        return Map.of("skuId", skuId, "stock", -1, "available", false,
                "source", "降级-默认无货",
                "tip", "库存查询失败，保守拒绝下单，请稍后重试");
    }

    private void sleep(long ms) {
        try { TimeUnit.MILLISECONDS.sleep(ms); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
