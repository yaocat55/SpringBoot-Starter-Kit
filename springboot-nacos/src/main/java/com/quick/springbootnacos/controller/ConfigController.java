package com.quick.springbootnacos.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Nacos 配置中心演示 —— @RefreshScope 让 @Value 实时生效。
 *
 * 使用方法：
 * 1. 在 Nacos 控制台创建 Data ID = nacos-demo.yaml 的配置
 * 2. 发布后，访问本接口即可读到最新值
 * 3. 修改配置并发布 → 不用重启应用，接口返回值立刻变化
 */
@RefreshScope   // ← 配置刷新关键注解
@RestController
@RequestMapping("/api/config")
public class ConfigController {

    /** 从 Nacos 配置中心读取，如果没有则用默认值 */
    @Value("${app.title:默认标题}")
    private String title;

    @Value("${app.version:1.0.0}")
    private String version;

    @Value("${app.author:未知}")
    private String author;

    /** 读所有配置 */
    @GetMapping
    public Map<String, String> getAll() {
        return Map.of("title", title, "version", version, "author", author);
    }

    /** 读单个配置 */
    @GetMapping("/title")
    public String getTitle() {
        return title;
    }
}
