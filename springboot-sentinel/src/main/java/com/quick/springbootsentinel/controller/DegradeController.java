package com.quick.springbootsentinel.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.quick.springbootsentinel.handler.CustomBlockHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 降级演示 Controller。
 * <p>
 * 降级 = 当服务不可用时，返回兜底数据而不是抛错误给用户。
 * 这里是纯业务层面的降级演示（不依赖熔断规则），通过 fallback 方法实现。
 */
@Slf4j
@RestController
@RequestMapping("/api/degrade")
public class DegradeController {

    // ======================== 推荐列表降级 ========================

    /**
     * 模拟"推荐接口"，正常情况下返回个性化推荐。
     * <p>
     * 当推荐服务挂了（抛异常）时，fallback 返回热门兜底数据。
     * <pre>
     *   # 正常时返回个性化推荐（偶尔失败触发降级）
     *   curl http://localhost:8085/api/degrade/recommend
     * </pre>
     */
    @GetMapping("/recommend")
    @SentinelResource(value = "recommend",
            fallback = "recommendFallback",
            blockHandler = "handleBlock",
            blockHandlerClass = CustomBlockHandler.class)
    public Map<String, Object> recommend() {
        // 模拟推荐服务偶尔故障
        if (ThreadLocalRandom.current().nextDouble() < 0.5) {
            throw new RuntimeException("推荐服务响应超时");
        }
        return Map.of("success", true, "data",
                new String[]{"个性化商品A", "个性化商品B", "个性化商品C"},
                "source", "实时推荐");
    }

    /** 推荐降级：返回热门榜单兜底 */
    public Map<String, Object> recommendFallback(Throwable t) {
        log.warn("[推荐降级] 推荐服务异常，返回兜底数据: {}", t.getMessage());
        return Map.of("success", true, "data",
                new String[]{"热门商品1", "热门商品2", "热门商品3"},
                "source", "热门兜底（降级）",
                "tip", "推荐服务暂时不可用，为您展示热门商品");
    }

    // ======================== 用户信息降级 ========================

    /**
     * 模拟"用户信息查询"，正常返回完整用户信息。
     * <p>
     * 下游服务异常时返回缓存版本或默认信息，
     * 保证用户看到的是"稍旧的正确数据"而不是"最新的错误页"。
     */
    @GetMapping("/user-info")
    @SentinelResource(value = "userInfo",
            fallback = "userInfoFallback",
            blockHandler = "handleBlock",
            blockHandlerClass = CustomBlockHandler.class)
    public Map<String, Object> userInfo() {
        if (ThreadLocalRandom.current().nextDouble() < 0.5) {
            throw new RuntimeException("用户服务调用失败");
        }
        return Map.of("success", true,
                "userName", "张三",
                "level", "VIP",
                "avatar", "https://cdn.example.com/avatar/123.jpg",
                "source", "实时查询");
    }

    public Map<String, Object> userInfoFallback(Throwable t) {
        log.warn("[用户信息降级] 用户服务异常，返回缓存: {}", t.getMessage());
        // 实际项目中这里可以查本地缓存
        return Map.of("success", true,
                "userName", "张三",
                "level", "VIP",
                "avatar", "/default-avatar.png",
                "source", "本地缓存（降级）",
                "updatedAt", "2026-05-25 10:00:00",
                "tip", "用户信息可能不是最新");
    }

}
