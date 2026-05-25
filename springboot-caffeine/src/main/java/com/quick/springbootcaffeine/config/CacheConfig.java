package com.quick.springbootcaffeine.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.concurrent.TimeUnit;

/**
 * Caffeine 缓存配置 —— 演示两种配置方式。
 * <ol>
 *   <li>yml 全局默认配置（application.yml 中的 spring.cache.caffeine.spec）</li>
 *   <li>编程式精细配置（本类中 userCacheManager / productCacheManager）</li>
 * </ol>
 * <p>
 * 生产建议：通用缓存用 yml 默认配置，有特殊 TTL 需求的用编程式单独配。
 */
@Configuration
@EnableCaching
public class CacheConfig {

    // ==================== 方式一：yml 全局默认 ====================
    // 见 application.yml: spring.cache.type=caffeine, spring.cache.caffeine.spec=...
    // Spring Boot 自动创建 CaffeineCacheManager，不需要写任何代码。

    // ==================== 方式二：编程式多 CacheManager（不同缓存不同策略） ====================

    /**
     * 默认 CacheManager —— yml 全局配置自动生效（无需额外代码）。
     * 标注 @Primary 确保 @Cacheable 默认使用此 Manager。
     */
    @Primary
    @Bean("defaultCacheManager")
    public CacheManager defaultCacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCaffeine(Caffeine.newBuilder()
                .initialCapacity(100)
                .maximumSize(500)
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .recordStats());          // 开启统计（/actuator/caches 可查看命中率）
        return manager;
    }

    /**
     * 用户缓存 —— TTL 更长，容量更大。
     * <p>
     * 使用方式：{@code @Cacheable(value = "users", cacheManager = "userCacheManager")}
     */
    @Bean("userCacheManager")
    public CacheManager userCacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCaffeine(Caffeine.newBuilder()
                .initialCapacity(200)
                .maximumSize(2000)
                .expireAfterAccess(30, TimeUnit.MINUTES)   // 30 分钟无访问则过期
                .expireAfterWrite(1, TimeUnit.HOURS)        // 最多存活 1 小时
                .recordStats());
        return manager;
    }

    /**
     * 商品缓存 —— TTL 短，数据时效性要求高。
     */
    @Bean("productCacheManager")
    public CacheManager productCacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCaffeine(Caffeine.newBuilder()
                .initialCapacity(50)
                .maximumSize(1000)
                .expireAfterWrite(2, TimeUnit.MINUTES)      // 2 分钟就过期
                .recordStats());
        return manager;
    }

    /**
     * 极速缓存 —— 无过期，纯手工淘汰。
     */
    @Bean("foreverCacheManager")
    public CacheManager foreverCacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(100)
                .recordStats());
        return manager;
    }
}
