package com.quick.springbootexception.controller;

import com.quick.springbootexception.common.Result;
import com.quick.springbootexception.model.User;
import com.quick.springbootexception.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 用户接口 —— 演示三层架构 + 全局异常处理。
 * <p>
 * Controller 层极其干净：只做参数绑定 + 调用 Service + 返回 Result。
 * 没有一行 try-catch，没有一行 if 判断返回值。
 * 所有异常由 Service 抛出 → GlobalExceptionHandler 统一兜底 → 返回统一 Result JSON。
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // ==================== 正常操作 —— Controller 里干干净净 ====================

    @GetMapping("/{id}")
    public Result<User> getById(@PathVariable Long id) {
        return Result.ok(userService.getById(id));
        // 如果用户不存在：Service throw NotFoundException → GlobalExceptionHandler 兜底
        // Controller 不需要写 if(user==null) return Result.fail(...)
    }

    @PostMapping
    public Result<User> add(@Valid @RequestBody User user) {
        // @Valid 校验失败：自动抛 MethodArgumentNotValidException → 全局异常处理兜底
        // Controller 不需要写 if(bindingResult.hasErrors()) ...
        return Result.ok(userService.add(user));
    }

    @PutMapping("/{id}")
    public Result<User> update(@PathVariable Long id, @Valid @RequestBody User user) {
        return Result.ok(userService.update(id, user));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return Result.ok();
    }

    // ==================== 演示各种异常场景 ====================

    /** 演示：参数格式错误（传了字符串 ID） */
    @GetMapping("/{id}/profile")
    public Result<User> profile(@PathVariable Long id) {
        return Result.ok(userService.getById(id));
    }

    /** 演示：服务端内部错误 */
    @PostMapping("/error/internal")
    public Result<Void> triggerInternalError() {
        userService.simulateInternalError();
        return Result.ok();
    }
}
