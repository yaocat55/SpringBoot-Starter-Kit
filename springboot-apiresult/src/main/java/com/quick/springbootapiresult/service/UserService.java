package com.quick.springbootapiresult.service;

import com.quick.springbootapiresult.common.PageRequest;
import com.quick.springbootapiresult.common.PageResult;
import com.quick.springbootapiresult.exception.BadRequestException;
import com.quick.springbootapiresult.exception.DuplicateException;
import com.quick.springbootapiresult.exception.NotFoundException;
import com.quick.springbootapiresult.mapper.UserMapper;
import com.quick.springbootapiresult.model.User;
import com.quick.springbootapiresult.model.UserQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;

    private static final Set<String> ALLOWED_ORDER_COLUMNS = Set.of("id", "name", "age", "email");

    // ==================== 简单分页 + 模糊 ====================

    public PageResult<User> search(String blurry, PageRequest page) {
        page.validate();
        validateSortField(page);

        long total = userMapper.countByBlurry(blurry);
        List<User> data = total > 0 ? userMapper.selectPage(blurry, page) : List.of();
        return PageResult.build(page, (int) total, data);
    }

    // ==================== 条件查询 ====================

    public PageResult<User> searchByCondition(UserQuery query) {
        query.validate();
        validateSortField(query);

        int total = userMapper.searchCount(query);
        List<User> data = total > 0 ? userMapper.searchByCondition(query) : List.of();
        return PageResult.build(query, total, data);
    }

    // ==================== CRUD ====================

    public User getById(Long id) {
        User user = userMapper.findById(id);
        if (user == null) {
            throw new NotFoundException("id=" + id);
        }
        return user;
    }

    public User add(User user) {
        User existing = userMapper.findByEmail(user.getEmail());
        if (existing != null) {
            throw new DuplicateException("邮箱已存在: " + user.getEmail());
        }
        if (user.getStatus() == null) user.setStatus("active");
        if (user.getRole() == null) user.setRole("viewer");

        userMapper.insert(user);
        log.info("新增用户: id={}, name={}", user.getId(), user.getName());
        return user;
    }

    public User update(Long id, User user) {
        User existing = userMapper.findById(id);
        if (existing == null) {
            throw new NotFoundException("id=" + id);
        }
        user.setId(id);
        userMapper.update(user);
        log.info("更新用户: id={}", id);
        return user;
    }

    public void delete(Long id) {
        User existing = userMapper.findById(id);
        if (existing == null) {
            throw new NotFoundException("id=" + id);
        }
        userMapper.deleteById(id);
        log.info("删除用户: id={}", id);
    }

    // ==================== 工具 ====================

    private void validateSortField(PageRequest page) {
        if (page.getSortField() == null || page.getSortField().isEmpty()) {
            return;
        }
        for (String field : page.getSortField()) {
            String col = field.split(",")[0].trim();
            if (!ALLOWED_ORDER_COLUMNS.contains(col)) {
                throw new BadRequestException("不支持的排序字段: " + col + "，允许: " + ALLOWED_ORDER_COLUMNS);
            }
        }
    }
}
