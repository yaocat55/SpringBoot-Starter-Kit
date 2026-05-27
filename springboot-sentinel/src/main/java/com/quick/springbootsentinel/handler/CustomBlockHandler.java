package com.quick.springbootsentinel.handler;

import com.alibaba.csp.sentinel.slots.block.BlockException;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * 统一 Block 处理类。
 * <p>
 * <h3>新手必读</h3>
 * blockHandler 方法签名必须同时满足：
 * <ul>
 *   <li>方法返回值与原方法一致</li>
 *   <li>参数列表与原方法完全一致 + 最后一个参数 BlockException</li>
 *   <li>必须是 static 方法（如果通过 blockHandlerClass 指定）</li>
 * </ul>
 * <p>
 * fallback 方法类似，只是最后一个参数改为 Throwable。
 */
@Slf4j
public final class CustomBlockHandler {

    private CustomBlockHandler() {}

    /**
     * 通用 block 处理 —— 返回友好的 JSON。
     * <p>
     * 这个方法的参数列表会随 @SentinelResource 的方法自动匹配。
     * 不同方法签名有不同参数的场景，可以在这里写多个重载方法。
     */
    public static Map<String, Object> handleBlock(BlockException e) {
        log.warn("[Sentinel Block] 规则={}, 资源={}", e.getRule().getResource(), e.getRule().getResource());
        return Map.of(
                "success", false,
                "code", 429,
                "msg", "请求被限流/熔断，请稍后重试",
                "rule", e.getRule().getResource(),
                "type", e.getClass().getSimpleName()
        );
    }

}
