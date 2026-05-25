package com.quick.springbootcaffeine.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 商品服务 —— 演示不同缓存策略（短 TTL）、sync 同步加载。
 */
@Slf4j
@Service
public class ProductService {

    private static final Map<Long, Map<String, Object>> DB = new ConcurrentHashMap<>();

    static {
        DB.put(1L, Map.of("id", 1L, "name", "iPhone 15", "price", new BigDecimal("6999")));
        DB.put(2L, Map.of("id", 2L, "name", "MacBook Pro", "price", new BigDecimal("14999")));
    }

    /**
     * 查商品 —— 使用 productCacheManager（2 分钟短 TTL）。
     * <p>
     * sync = true 的含义：当缓存过期时，多个并发请求只有一个会去查 DB 重建缓存，
     * 其他请求阻塞等待（而不是雪崩式全去查 DB）。适用于高并发热点数据。
     */
    @Cacheable(value = "products", key = "#id", cacheManager = "productCacheManager", sync = true)
    public Map<String, Object> getProductById(Long id) {
        log.info("【ProductService】缓存未命中，查 DB: id={}", id);
        sleep(500);
        Map<String, Object> product = DB.get(id);
        if (product == null) {
            throw new RuntimeException("商品不存在: " + id);
        }
        return product;
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
