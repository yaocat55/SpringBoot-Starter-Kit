package com.quick.springbootredis.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SentinelServersConfig;
import org.redisson.config.SingleServerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson 客户端配置。
 * <p>
 * 优先使用 spring.data.redis 下的连接信息（host/port/password/database），
 * 同时也为 Sentinel / Cluster 模式提供独立 Bean，按需激活。
 * <p>
 * 接入方式：直接注入 {@link RedissonClient} 即可使用。
 */
@Configuration
public class RedissonConfig {

    // ==================== 单机模式（默认激活） ====================
    @Bean(destroyMethod = "shutdown")
    @ConditionalOnProperty(name = "spring.data.redis.host")
    public RedissonClient redissonClient(
            @Value("${spring.data.redis.host}") String host,
            @Value("${spring.data.redis.port}") int port,
            @Value("${spring.data.redis.password:}") String password,
            @Value("${spring.data.redis.database:0}") int database) {

        Config config = new Config();
        SingleServerConfig serverConfig = config.useSingleServer()
                .setAddress("redis://" + host + ":" + port)
                .setDatabase(database)
                .setConnectionPoolSize(16)
                .setConnectionMinimumIdleSize(4);

        if (password != null && !password.isBlank()) {
            serverConfig.setPassword(password);
        }

        return Redisson.create(config);
    }

    // ==================== 哨兵模式（激活需配置 sentinel.nodes） ====================
    @Bean(destroyMethod = "shutdown")
    @ConditionalOnProperty(name = "spring.data.redis.sentinel.nodes")
    public RedissonClient redissonSentinelClient(
            @Value("${spring.data.redis.sentinel.master}") String master,
            @Value("${spring.data.redis.sentinel.nodes}") String nodes,
            @Value("${spring.data.redis.password:}") String password,
            @Value("${spring.data.redis.database:0}") int database) {

        Config config = new Config();
        SentinelServersConfig sentinelConfig = config.useSentinelServers()
                .setMasterName(master)
                .setDatabase(database);

        for (String node : nodes.split(",")) {
            sentinelConfig.addSentinelAddress("redis://" + node.trim());
        }

        if (password != null && !password.isBlank()) {
            sentinelConfig.setPassword(password);
        }

        return Redisson.create(config);
    }
}
