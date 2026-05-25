package com.quick.springbootlombok.service;

import com.quick.springbootlombok.model.User;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * UserService —— 演示 @Slf4j @RequiredArgsConstructor @SneakyThrows。
 * <p>
 * {@code @Slf4j}      自动生成 log 字段，相当于 LoggerFactory.getLogger(UserService.class)
 * {@code @RequiredArgsConstructor} 为 final 字段生成构造器，Spring 自动注入
 * {@code @SneakyThrows} 悄悄抛出受检异常，无需在方法签名上声明 throws
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final List<User> userStore = new ArrayList<>();

    public User create(User user) {
        userStore.add(user);
        log.info("创建用户: {}", user.getUsername());
        return user;
    }

    public List<User> listAll() {
        log.info("查询所有用户, 共 {} 条", userStore.size());
        return userStore;
    }

    @SneakyThrows
    public User findOrThrow(Long id) {
        // 模拟一个受检异常 —— 无需在方法签名上写 throws Exception
        if (id <= 0) {
            throw new Exception("ID 必须大于 0");
        }
        return userStore.stream()
                .filter(u -> u.getId().equals(id))
                .findFirst()
                .orElse(null);
    }
}
