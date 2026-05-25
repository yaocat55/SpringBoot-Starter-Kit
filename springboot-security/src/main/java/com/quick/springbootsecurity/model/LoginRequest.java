package com.quick.springbootsecurity.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录请求体。
 * <p>
 * 支持三种登录方式：
 * <ol>
 *   <li>用户名 + 密码</li>
 *   <li>邮箱 + 密码</li>
 *   <li>手机号 + 验证码（smsCode 非空时走短信登录）</li>
 * </ol>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {

    /** 用户名 / 邮箱 / 手机号（至少填一个） */
    private String username;
    /** 密码（密码登录时必填） */
    private String password;
    /** 短信验证码（短信登录时填写，此时 password 可为空） */
    private String smsCode;
}
