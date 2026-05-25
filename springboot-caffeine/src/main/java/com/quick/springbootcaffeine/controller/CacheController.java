package com.quick.springbootcaffeine.controller;

import com.quick.springbootcaffeine.model.User;
import com.quick.springbootcaffeine.service.ProductService;
import com.quick.springbootcaffeine.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST 接口 —— 直观感受缓存命中/未命中的响应速度差异。
 * <p>
 * 使用方法：同一个接口调两次。第一次慢（查 DB，打印 【缓存未命中】 日志），
 * 第二次快（走缓存，控制台无日志），肉眼可见缓存加速效果。
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CacheController {

    private final UserService userService;
    private final ProductService productService;

    // ==================== @Cacheable 演示 ====================

    /** 查用户（默认缓存 10 分钟 TTL） */
    @GetMapping("/users/{id}")
    public User getUser(@PathVariable Long id) {
        return userService.getUserById(id);
    }

    /** 查用户（自定义 30 分钟 TTL） */
    @GetMapping("/users/{id}/custom-ttl")
    public User getUserWithCustomTTL(@PathVariable Long id) {
        return userService.getUserByIdWithCustomTTL(id);
    }

    /** 查用户（带条件缓存） */
    @GetMapping("/users/{id}/adult")
    public User getUserIfAdult(@PathVariable Long id) {
        return userService.getUserByIdIfAdult(id);
    }

    /** 查用户（null 不缓存，防穿透） */
    @GetMapping("/users/{id}/unless-null")
    public User getUserUnlessNull(@PathVariable Long id) {
        return userService.getUserByIdUnlessNull(id);
    }

    // ==================== @CachePut 演示 ====================

    /** 新增用户 —— 同时写库 + 写缓存 */
    @PostMapping("/users")
    public User addUser(@RequestBody User user) {
        return userService.addUser(user);
    }

    /** 更新用户 —— 同时更新库 + 更新缓存 */
    @PutMapping("/users/{id}")
    public User updateUser(@PathVariable Long id, @RequestBody User user) {
        user.setId(id);
        return userService.updateUser(user);
    }

    // ==================== @CacheEvict 演示 ====================

    /** 删除用户 —— 同时删库 + 清指定缓存 */
    @DeleteMapping("/users/{id}")
    public Map<String, Object> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return Map.of("success", true, "deletedId", id);
    }

    /** 清空全部 users 缓存 */
    @DeleteMapping("/users/cache")
    public Map<String, Object> clearUsersCache() {
        userService.clearAllUsersCache();
        return Map.of("success", true, "note", "users 缓存已全部清空，下次查会走 DB");
    }

    // ==================== @Caching 组合演示 ====================

    /** 保存并刷新缓存（组合 @CacheEvict + @CachePut） */
    @PutMapping("/users/{id}/refresh")
    public User saveAndRefresh(@PathVariable Long id, @RequestBody User user) {
        user.setId(id);
        return userService.saveAndRefresh(user);
    }

    // ==================== Product 缓存演示（短 TTL + sync） ====================

    @GetMapping("/products/{id}")
    public Map<String, Object> getProduct(@PathVariable Long id) {
        return productService.getProductById(id);
    }

    // ==================== 缓存效果对比 ====================

    /**
     * 手动触发两次查同一个用户，直观对比耗时。
     */
    @GetMapping("/benchmark/{id}")
    public Map<String, Object> benchmark(@PathVariable Long id) {
        // 先清缓存保证公平
        userService.clearAllUsersCache();

        long t1 = System.currentTimeMillis();
        userService.getUserById(id);
        long firstCall = System.currentTimeMillis() - t1;

        long t2 = System.currentTimeMillis();
        userService.getUserById(id);
        long secondCall = System.currentTimeMillis() - t2;

        return Map.of(
                "firstCallMs", firstCall,
                "secondCallMs", secondCall,
                "speedUp", firstCall / Math.max(secondCall, 1) + "x",
                "note", "第一次查 DB（慢），第二次走缓存（快）"
        );
    }
}
