package com.quick.springbootopenfeign.client;

import com.quick.springbootopenfeign.model.User;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * OpenFeign 声明式客户端 —— 核心代码。
 * <p>
 * 只需要定义一个接口 + 注解，OpenFeign 自动生成 HTTP 代理实现。
 * 对比手写 RestTemplate：
 * <pre>{@code
 *   // 不用 Feign：
 *   String url = "http://localhost:8086/api/users/" + id;
 *   User user = restTemplate.getForObject(url, User.class);
 *
 *   // 用 Feign：
 *   User user = userFeignClient.getById(id);
 * }</pre>
 * <p>
 * url 参数用于本地演示直连，生产环境删掉 url，用 Nacos 自动发现。
 */
@FeignClient(
        name = "user-service",
        url = "http://localhost:8086",
        fallback = UserFeignFallback.class,
        configuration = UserFeignConfig.class
)
public interface UserFeignClient {

    @GetMapping("/api/users/{id}")
    User getById(@PathVariable Long id);

    @GetMapping("/api/users")
    List<User> listAll();

    @PostMapping("/api/users")
    User create(@RequestBody User user);

    @PutMapping("/api/users/{id}")
    User update(@PathVariable Long id, @RequestBody User user);

    @DeleteMapping("/api/users/{id}")
    Map<String, Object> delete(@PathVariable Long id);
}
