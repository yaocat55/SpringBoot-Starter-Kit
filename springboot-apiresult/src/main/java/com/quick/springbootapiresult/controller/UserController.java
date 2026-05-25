package com.quick.springbootapiresult.controller;

import com.quick.springbootapiresult.common.ApiResult;
import com.quick.springbootapiresult.common.PageRequest;
import com.quick.springbootapiresult.common.PageResult;
import com.quick.springbootapiresult.model.User;
import com.quick.springbootapiresult.model.UserQuery;
import com.quick.springbootapiresult.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 用户接口 —— 演示 4 种典型使用方式。
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // ============================================================
    //  演示 1：简单分页 + 模糊查询
    //  GET /api/users?blurry=张&pageNo=1&pageSize=5&sortField=age,desc
    // ============================================================
    @GetMapping
    public ApiResult<PageResult<User>> list(@RequestParam(required = false) String blurry,
                                             PageRequest page) {
        page.validate();
        return ApiResult.success(userService.search(blurry, page));
    }

    // ============================================================
    //  演示 2：生产级条件查询（UserQuery：分页+模糊+日期+精确条件合一）
    //  GET /api/users/condition?blurry=张&status=active&role=admin&betweenTime=2025-01-01,2025-12-31&pageNo=1&pageSize=10&sortField=age,desc
    // ============================================================
    @GetMapping("/condition")
    public ApiResult<PageResult<User>> conditionSearch(UserQuery query) {
        return ApiResult.success(userService.searchByCondition(query));
    }

    // ============================================================
    //  演示 3：BizException 全局兜底
    // ============================================================
    @GetMapping("/{id}")
    public ApiResult<User> getById(@PathVariable Long id) {
        return ApiResult.success(userService.getById(id));
    }

    @PostMapping
    public ApiResult<User> add(@Valid @RequestBody User user) {
        return ApiResult.success(userService.add(user));
    }

    @PutMapping("/{id}")
    public ApiResult<User> update(@PathVariable Long id, @RequestBody User user) {
        return ApiResult.success(userService.update(id, user));
    }

    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return ApiResult.success();
    }

    // ============================================================
    //  演示 4：ResponseAdvice 自动包装
    // ============================================================

    @GetMapping("/hello")
    public String hello() {
        return "hello world";
    }

    @GetMapping("/first")
    public User getFirst() {
        return userService.getById(1L);
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        return Map.of("totalUsers", 20, "activeUsers", 14, "adminCount", 4);
    }

    @GetMapping("/exists/{name}")
    public Boolean checkExists(@PathVariable String name) {
        return true;
    }
}
