package com.quick.springbootcaffeine.service;

import com.quick.springbootcaffeine.model.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 用户服务 —— 演示 Spring Cache 三大注解：
 * <ul>
 *   <li>{@code @Cacheable}   —— 查：缓存有则返回缓存，无则查库并自动写入缓存</li>
 *   <li>{@code @CachePut}    —— 改：强制更新缓存（不管缓存里有没有）</li>
 *   <li>{@code @CacheEvict}  —— 删：清除缓存</li>
 * </ul>
 * <p>
 * 模拟数据库：用 ConcurrentHashMap 假装是 DB，方法内部有 sleep 模拟慢查询。
 * 对比首次调用（无缓存，慢）和第二次调用（有缓存，快）的响应时间，直观感受缓存效果。
 */
@Slf4j
@Service
public class UserService {

    /** 假装是数据库 */
    private static final Map<Long, User> DB = new ConcurrentHashMap<>();

    static {
        DB.put(1L, User.of(1L, "张三", "zhangsan@example.com", 28));
        DB.put(2L, User.of(2L, "李四", "lisi@example.com", 35));
        DB.put(3L, User.of(3L, "王五", "wangwu@example.com", 22));
    }

    // ==================== @Cacheable —— 先查缓存，没有再查库 ====================

    /**
     * 按 ID 查用户 —— 缓存 key 为用户 ID。
     * <p>
     * 首次访问：sleep 500ms 模拟慢 SQL → 存入 users 缓存 → 返回
     * 后续访问：直接从 Caffeine 返回，耗时 < 1ms
     */
    @Cacheable(value = "users", key = "#id")
    public User getUserById(Long id) {
        log.info("【@Cacheable】缓存未命中，查 DB: id={}", id);
        sleep(500);  // 模拟慢查询
        User user = DB.get(id);
        if (user == null) {
            throw new RuntimeException("用户不存在: " + id);
        }
        return user;
    }

    /**
     * 按 ID 查用户 —— 使用 userCacheManager（30 分钟 TTL）。
     * <p>
     * 演示不同缓存使用不同的 CacheManager。
     */
    @Cacheable(value = "users", key = "#id", cacheManager = "userCacheManager")
    public User getUserByIdWithCustomTTL(Long id) {
        log.info("【@Cacheable + userCacheManager】缓存未命中，查 DB: id={}", id);
        sleep(500);
        User user = DB.get(id);
        if (user == null) {
            throw new RuntimeException("用户不存在: " + id);
        }
        return user;
    }

    /**
     * 条件缓存：只缓存年龄 >= 18 的用户。
     * condition 为 true 时才走缓存，SpEL 表达式。
     */
    @Cacheable(value = "users", key = "#id", condition = "#id != null")
    public User getUserByIdIfAdult(Long id) {
        log.info("【@Cacheable + condition】缓存未命中，查 DB: id={}", id);
        sleep(300);
        return DB.get(id);
    }

    /**
     * unless：当返回值为 null 时不缓存。
     * 适用于"查不到的数据不缓存"的场景，避免缓存穿透。
     */
    @Cacheable(value = "users", key = "#id", unless = "#result == null")
    public User getUserByIdUnlessNull(Long id) {
        log.info("【@Cacheable + unless】缓存未命中，查 DB: id={}", id);
        sleep(300);
        return DB.get(id);  // id 不存在返回 null，不缓存
    }

    // ==================== @CachePut —— 强制更新缓存 ====================

    /**
     * 更新用户 —— 同时更新 DB 和缓存。
     * <p>
     * 与 @Cacheable 不同，@CachePut 每次都会执行方法体，然后把返回值写入缓存。
     * 适用场景：增/改操作后立即刷新缓存。
     */
    @CachePut(value = "users", key = "#user.id")
    public User updateUser(User user) {
        log.info("【@CachePut】更新 DB + 缓存: id={}", user.getId());
        sleep(200);
        DB.put(user.getId(), user);
        return user;
    }

    /**
     * 新增用户 —— 用 @CachePut 写入缓存，下次查直接从缓存拿。
     */
    @CachePut(value = "users", key = "#user.id")
    public User addUser(User user) {
        log.info("【@CachePut】新增用户: id={}, name={}", user.getId(), user.getName());
        DB.put(user.getId(), user);
        return user;
    }

    // ==================== @CacheEvict —— 清除缓存 ====================

    /**
     * 按 ID 删除用户 —— 同时删 DB 和缓存。
     * <p>
     * beforeInvocation = false（默认）: 方法执行成功后才清缓存。
     * 如果方法抛异常，缓存保留，保证下次还能读到旧数据。
     */
    @CacheEvict(value = "users", key = "#id")
    public void deleteUser(Long id) {
        log.info("【@CacheEvict】删除 DB 数据 + 清缓存: id={}", id);
        sleep(100);
        DB.remove(id);
    }

    /**
     * 清空整个 users 缓存。
     * allEntries = true 时 key 属性无效。
     */
    @CacheEvict(value = "users", allEntries = true)
    public void clearAllUsersCache() {
        log.info("【@CacheEvict allEntries】清空 users 缓存全部条目");
        // 只清缓存不清 DB
    }

    // ==================== 多缓存组合 ====================

    /**
     * 同一方法操作多个缓存。
     * <p>
     * {@code @Caching} 可以组合多个缓存注解。
     */
    @CacheEvict(value = "users", key = "#user.id")
    @CachePut(value = "users", key = "#user.id")
    public User saveAndRefresh(User user) {
        log.info("【@Caching】先删缓存再更新: id={}", user.getId());
        DB.put(user.getId(), user);
        return user;
    }

    // ==================== 工具方法 ====================

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
