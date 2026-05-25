package com.quick.springbootcaffeine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

/**
 * Caffeine 本地缓存演示应用。
 * <p>
 * {@code @EnableCaching} 开启 Spring Cache 注解驱动（@Cacheable / @CachePut / @CacheEvict）。
 * 引入 Caffeine 后 Spring Boot 自动配置 CaffeineCacheManager，零额外配置即可用。
 */
@SpringBootApplication
@EnableCaching
public class SpringbootCaffeineApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringbootCaffeineApplication.class, args);
    }
}
