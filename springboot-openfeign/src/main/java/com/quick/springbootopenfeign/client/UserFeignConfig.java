package com.quick.springbootopenfeign.client;

import feign.Logger;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;

/**
 * Feign 客户端配置 —— 拦截器、日志级别等。
 * <p>
 * 在 {@code @FeignClient(configuration=...)} 中引用，
 * 这些配置只对当前 FeignClient 生效，不影响其他 FeignClient。
 */
@Slf4j
public class UserFeignConfig {

    /**
     * 请求拦截器 —— 在每次 Feign 发起 HTTP 请求前执行。
     * <p>
     * 典型用途：
     * <ul>
     *   <li>添加认证 Token</li>
     *   <li>添加链路追踪 ID（traceId）</li>
     *   <li>添加公共请求头</li>
     * </ul>
     */
    @Bean
    public RequestInterceptor requestInterceptor() {
        return new RequestInterceptor() {
            @Override
            public void apply(RequestTemplate template) {
                log.info("[Feign拦截器] {} {}", template.method(), template.url());
                // 示例：添加自定义 Header
                template.header("X-Caller", "springboot-openfeign");
                template.header("X-Trace-Id", java.util.UUID.randomUUID().toString());
            }
        };
    }

    /**
     * Feign 日志级别。
     * <ul>
     *   <li>NONE：不打日志</li>
     *   <li>BASIC：只记录请求方法和 URL + 响应状态码和执行时间</li>
     *   <li>HEADERS：记录 BASIC + 请求和响应的 Header</li>
     *   <li>FULL：记录所有（请求/响应 Header、Body、元数据）</li>
     * </ul>
     */
    @Bean
    public Logger.Level feignLoggerLevel() {
        return Logger.Level.FULL;
    }
}
