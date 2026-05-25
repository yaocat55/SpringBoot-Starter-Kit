package com.quick.springbootsatoken.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录请求 DTO。
 * <p>
 * 支持多种登录方式：用户名+密码、手机号+验证码。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {

    /** 用户名 / 手机号 / 邮箱 */
    @NotBlank(message = "用户名不能为空")
    private String username;

    /** 密码 */
    @Size(min = 6, max = 64, message = "密码长度 6-64 位")
    private String password;

    /** 短信验证码（手机号登录时使用） */
    private String smsCode;
}
