package com.quick.springbootopenfeign.server;

import com.quick.springbootopenfeign.model.User;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 【服务端】标准 Spring MVC REST 控制器 —— 模拟"下游服务"。
 * <p>
 * 这才是真实的"服务提供者"。在下游服务中，这些就是普通的 Controller。
 * OpenFeign 客户端通过 HTTP 调用这些接口，就像调本地方法一样。
 * <p>
 * 数据存内存（ConcurrentHashMap），重启丢失，仅用于演示。
 */
@Slf4j
@RestController
@RequestMapping("/api/users")
public class UserServerController {

    private final Map<Long, User> userStore = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(0);

    @PostConstruct
    void initSampleData() {
        // 预置几条示例数据
        save(User.of(nextId(), "张三", "zhangsan@example.com"));
        save(User.of(nextId(), "李四", "lisi@example.com"));
        save(User.of(nextId(), "王五", "wangwu@example.com"));
        log.info("[Server] 预置 {} 条用户数据", userStore.size());
    }

    private Long nextId() {
        return idGenerator.incrementAndGet();
    }

    private void save(User user) {
        userStore.put(user.getId(), user);
    }

    /** 查询单个用户 */
    @GetMapping("/{id}")
    public User getById(@PathVariable Long id) {
        log.info("[Server] GET /api/users/{}", id);
        User user = userStore.get(id);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在: " + id);
        }
        return user;
    }

    /** 查询全部用户 */
    @GetMapping
    public List<User> listAll() {
        log.info("[Server] GET /api/users");
        return userStore.values().stream().toList();
    }

    /** 创建用户 */
    @PostMapping
    public User create(@RequestBody User user) {
        user.setId(nextId());
        save(user);
        log.info("[Server] POST /api/users -> id={}, name={}", user.getId(), user.getName());
        return user;
    }

    /** 更新用户 */
    @PutMapping("/{id}")
    public User update(@PathVariable Long id, @RequestBody User user) {
        log.info("[Server] PUT /api/users/{}", id);
        if (!userStore.containsKey(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在: " + id);
        }
        user.setId(id);
        save(user);
        return user;
    }

    /** 删除用户 */
    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable Long id) {
        log.info("[Server] DELETE /api/users/{}", id);
        if (userStore.remove(id) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在: " + id);
        }
        return Map.of("success", true, "deletedId", id);
    }
}
