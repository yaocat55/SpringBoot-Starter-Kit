package com.quick.springbootsatoken.config;

import cn.dev33.satoken.stp.StpInterface;
import com.quick.springbootsatoken.model.LoginUser;
import com.quick.springbootsatoken.service.UserService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Sa-Token 权限与角色加载接口实现。
 * <p>
 * 每次鉴权（@SaCheckPermission / @SaCheckRole / StpUtil.checkPermission）时，
 * Sa-Token 会回调这两个方法，根据 loginId 查询用户拥有的权限码和角色码。
 */
@Component
public class StpInterfaceImpl implements StpInterface {

    private final UserService userService;

    public StpInterfaceImpl(UserService userService) {
        this.userService = userService;
    }

    /**
     * 返回用户拥有的权限码集合。
     */
    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        LoginUser user = userService.getByUserId(Long.valueOf(loginId.toString()));
        if (user == null || user.getPermissions() == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(user.getPermissions());
    }

    /**
     * 返回用户拥有的角色码集合。
     */
    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        LoginUser user = userService.getByUserId(Long.valueOf(loginId.toString()));
        if (user == null || user.getRoles() == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(user.getRoles());
    }
}
