package com.quick.springbootlombok.controller;

import com.quick.springbootlombok.model.User;
import com.quick.springbootlombok.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * UserController —— 演示 @Slf4j + @RequiredArgsConstructor 在 Controller 中的用法。
 */
@Slf4j
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    public User create(@RequestBody User user) {
        log.info("收到创建用户请求: {}", user);
        return userService.create(user);
    }

    @GetMapping
    public List<User> listAll() {
        return userService.listAll();
    }

    @GetMapping("/{id}")
    public User getById(@PathVariable Long id) {
        return userService.findOrThrow(id);
    }
}
