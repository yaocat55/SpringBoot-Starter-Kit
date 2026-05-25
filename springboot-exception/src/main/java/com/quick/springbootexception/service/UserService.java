package com.quick.springbootexception.service;

import com.quick.springbootexception.exception.BadRequestException;
import com.quick.springbootexception.exception.DuplicateKeyException;
import com.quick.springbootexception.exception.NotFoundException;
import com.quick.springbootexception.exception.ServiceException;
import com.quick.springbootexception.model.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 用户服务 —— 演示 Service 层如何抛异常。
 * <p>
 * 三层架构中的异常处理原则：
 * <ul>
 *   <li>Controller 层：只做参数绑定和路由，不写 try-catch</li>
 *   <li>Service 层：遇到业务错误直接 throw XxxException，让全局异常处理器兜底</li>
 *   <li>Repository 层：数据不存在返回 Optional.empty()，不要 throw</li>
 * </ul>
 */
@Slf4j
@Service
public class UserService {

    /** 假装是数据库 */
    private static final Map<Long, User> DB = new ConcurrentHashMap<>();
    private static final AtomicLong ID_GEN = new AtomicLong(3);

    static {
        DB.put(1L, new User(1L, "张三", "zhangsan@example.com", 28));
        DB.put(2L, new User(2L, "李四", "lisi@example.com", 35));
    }

    /**
     * 按 ID 查用户 —— 查不到直接 throw NotFoundException。
     * <p>
     * Controller 不用写 if(user==null) return Result.fail(...)
     * Service 直接 throw，全局异常处理器统一转成 Result JSON。
     */
    public User getById(Long id) {
        if (id == null || id <= 0) {
            throw new BadRequestException("用户 ID 必须大于 0，收到: " + id);
        }
        User user = DB.get(id);
        if (user == null) {
            throw new NotFoundException("用户", id);
        }
        return user;
    }

    /**
     * 新增用户 —— 重名抛 DuplicateKeyException。
     */
    public User add(User user) {
        // 模拟重名检查
        boolean nameExists = DB.values().stream()
                .anyMatch(u -> u.getName().equals(user.getName()));
        if (nameExists) {
            throw new DuplicateKeyException("用户名已存在: " + user.getName());
        }

        long id = ID_GEN.incrementAndGet();
        user.setId(id);
        DB.put(id, user);
        log.info("新增用户: id={}, name={}", id, user.getName());
        return user;
    }

    /**
     * 更新用户 —— 不存在抛 NotFoundException。
     */
    public User update(Long id, User user) {
        if (!DB.containsKey(id)) {
            throw new NotFoundException("用户", id);
        }
        user.setId(id);
        DB.put(id, user);
        log.info("更新用户: id={}", id);
        return user;
    }

    /**
     * 删除用户 —— 不存在抛 NotFoundException。
     */
    public void delete(Long id) {
        User removed = DB.remove(id);
        if (removed == null) {
            throw new NotFoundException("用户", id);
        }
        log.info("删除用户: id={}", id);
    }

    /**
     * 模拟服务内部错误（比如 DB 挂了）。
     */
    public void simulateInternalError() {
        try {
            int x = 1 / 0;  // ArithmeticException
        } catch (Exception e) {
            throw new ServiceException("模拟内部错误：除零异常", e);
        }
    }
}
