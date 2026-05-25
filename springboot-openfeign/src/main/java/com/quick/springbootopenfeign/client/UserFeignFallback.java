package com.quick.springbootopenfeign.client;

import com.quick.springbootopenfeign.model.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Feign 降级实现 —— 当下游服务不可用时自动走这里。
 * <p>
 * 触发场景：连接超时、读取超时、下游返回 5xx 等。
 * Fallback 返回兜底数据，防止上游被下游拖垮（熔断）。
 * <p>
 * 注册为 Spring Bean：{@code @Component} + 在 {@code @FeignClient(fallback=...)} 中引用。
 */
@Slf4j
@Component
public class UserFeignFallback implements UserFeignClient {

    @Override
    public User getById(Long id) {
        log.warn("[Fallback] getById({}) 触发降级，返回默认用户", id);
        return User.of(id, "降级用户", "fallback@example.com");
    }

    @Override
    public List<User> listAll() {
        log.warn("[Fallback] listAll() 触发降级，返回空列表");
        return Collections.emptyList();
    }

    @Override
    public User create(User user) {
        log.warn("[Fallback] create() 触发降级");
        return User.of(-1L, "创建失败(降级)", "n/a");
    }

    @Override
    public User update(Long id, User user) {
        log.warn("[Fallback] update({}) 触发降级", id);
        return User.of(id, "更新失败(降级)", "n/a");
    }

    @Override
    public Map<String, Object> delete(Long id) {
        log.warn("[Fallback] delete({}) 触发降级", id);
        return Map.of("success", false, "deletedId", id, "note", "降级响应");
    }
}
