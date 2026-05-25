package com.quick.springbootopenfeign.controller;

import com.quick.springbootopenfeign.client.UserFeignClient;
import com.quick.springbootopenfeign.client.UserFeignService;
import com.quick.springbootopenfeign.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST 控制器 —— 验证 OpenFeign 调用链路。
 * <p>
 * 提供两种调用方式便于对比：
 * <ul>
 *   <li>通过 {@link UserFeignService} 封装调用（推荐生产使用）</li>
 *   <li>通过 {@code @Autowired UserFeignClient} 直接调用（零封装，演示用）</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/feign")
@RequiredArgsConstructor
public class FeignTestController {

    private final UserFeignService feignService;
    private final UserFeignClient feignClient;

    // ======================== 通过 Service 封装调用（推荐） ========================

    @GetMapping("/service/user/{id}")
    public Map<String, Object> serviceGetUser(@PathVariable Long id) {
        User user = feignService.getUser(id);
        return Map.of("success", true, "user", user, "caller", "UserFeignService");
    }

    @GetMapping("/service/users")
    public Map<String, Object> serviceListAll() {
        List<User> users = feignService.getAllUsers();
        return Map.of("success", true, "count", users.size(), "users", users, "caller", "UserFeignService");
    }

    @PostMapping("/service/user")
    public Map<String, Object> serviceCreate(@RequestParam String name, @RequestParam String email) {
        User user = feignService.addUser(name, email);
        return Map.of("success", true, "user", user, "caller", "UserFeignService");
    }

    @PutMapping("/service/user/{id}")
    public Map<String, Object> serviceUpdate(@PathVariable Long id,
                                              @RequestParam String name,
                                              @RequestParam String email) {
        User user = feignService.modifyUser(id, name, email);
        return Map.of("success", true, "user", user, "caller", "UserFeignService");
    }

    @DeleteMapping("/service/user/{id}")
    public Map<String, Object> serviceDelete(@PathVariable Long id) {
        Map<String, Object> result = feignService.removeUser(id);
        return Map.of("success", true, "result", result, "caller", "UserFeignService");
    }

    // ======================== 直接通过 FeignClient 调用（演示用） ========================

    @GetMapping("/direct/user/{id}")
    public Map<String, Object> directGetUser(@PathVariable Long id) {
        User user = feignClient.getById(id);
        return Map.of("success", true, "user", user, "caller", "@FeignClient");
    }

    @GetMapping("/direct/users")
    public Map<String, Object> directListAll() {
        List<User> users = feignClient.listAll();
        return Map.of("success", true, "count", users.size(), "users", users, "caller", "@FeignClient");
    }

    @PostMapping("/direct/user")
    public Map<String, Object> directCreate(@RequestParam String name, @RequestParam String email) {
        User user = feignClient.create(new User(null, name, email));
        return Map.of("success", true, "user", user, "caller", "@FeignClient");
    }

    // ======================== 对比测试 ========================

    /** 同时用 FeignClient 和直接 HTTP 调用，结果应一致 */
    @GetMapping("/compare")
    public Map<String, Object> compare(@RequestParam(defaultValue = "1") Long id) {
        User viaFeign = feignClient.getById(id);
        return Map.of("success", true,
                "viaFeign", viaFeign,
                "note", "底层都是 HTTP + JSON 调用 /api/users/" + id);
    }

    /** 返回 Server 端所有用户列表 + Feign 调用链路说明 */
    @GetMapping("/info")
    public Map<String, Object> info() {
        return Map.of(
                "role", "OpenFeign 演示应用",
                "serverPort", "8086（REST Server / 下游服务）",
                "feignClient", "UserFeignClient → http://localhost:8086/api/users/*",
                "callChain", "浏览器 → FeignTestController → UserFeignClient(代理) → HTTP → UserServerController",
                "keyPoints", List.of(
                        "开发只写 interface，OpenFeign 自动生成 HTTP 代理",
                        "跟 Spring MVC 注解完全一致，学一个会两个",
                        "Fallback 机制天然支持熔断降级",
                        "与 RestTemplate 相比，代码量减少 70%+"
                )
        );
    }
}
