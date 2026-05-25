package com.quick.springbootsatoken.util;

import cn.dev33.satoken.stp.StpUtil;
import com.quick.springbootsatoken.model.LoginUser;

import java.util.List;

/**
 * Sa-Token 快捷工具 —— 一行代码完成常见鉴权操作。
 * <p>
 * 与直接使用 StpUtil 的区别：StpUtil 是 Sa-Token 原生 API，本类封装了
 * Session 中取 LoginUser 的逻辑，减少重复代码。
 */
public final class SaTokenUtil {

    private SaTokenUtil() {
    }

    // ==================== 登录状态 ====================

    /** 当前是否已登录 */
    public static boolean isLoggedIn() {
        return StpUtil.isLogin();
    }

    /** 当前登录用户 ID */
    public static Long getUserId() {
        return StpUtil.getLoginIdAsLong();
    }

    // ==================== 用户信息（从 Token Session 取） ====================

    /** 获取当前登录用户的完整信息 */
    public static LoginUser getLoginUser() {
        return (LoginUser) StpUtil.getSession().get("loginUser");
    }

    /** 获取当前用户昵称 */
    public static String getNickname() {
        LoginUser user = getLoginUser();
        return user != null ? user.getNickname() : "";
    }

    // ==================== 鉴权 ====================

    /** 检查是否拥有指定角色（无则抛异常） */
    public static void checkRole(String role) {
        StpUtil.checkRole(role);
    }

    /** 检查是否拥有指定权限（无则抛异常） */
    public static void checkPermission(String permission) {
        StpUtil.checkPermission(permission);
    }

    /** 是否有指定角色 */
    public static boolean hasRole(String role) {
        return StpUtil.hasRole(role);
    }

    /** 是否有指定权限 */
    public static boolean hasPermission(String permission) {
        return StpUtil.hasPermission(permission);
    }

    /** 当前用户拥有的角色列表 */
    public static List<String> getRoleList() {
        return StpUtil.getRoleList();
    }

    /** 当前用户拥有的权限列表 */
    public static List<String> getPermissionList() {
        return StpUtil.getPermissionList();
    }
}
