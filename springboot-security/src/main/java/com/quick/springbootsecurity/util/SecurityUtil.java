package com.quick.springbootsecurity.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

/**
 * 安全通用工具类 —— 密码加密、随机字符串生成等。
 */
public final class SecurityUtil {

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private SecurityUtil() {}

    // ==================== 密码操作 ====================

    /** 明文 → BCrypt 密文 */
    public static String encodePassword(String rawPassword) {
        return ENCODER.encode(rawPassword);
    }

    /** 校验明文与密文是否匹配 */
    public static boolean matchesPassword(String rawPassword, String encodedPassword) {
        return ENCODER.matches(rawPassword, encodedPassword);
    }

    // ==================== 随机字符串 ====================

    /** 生成 UUID（去掉横线） */
    public static String uuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /** 生成指定字节数的随机 Secure Base64 字符串 */
    public static String randomBase64(int bytes) {
        byte[] buffer = new byte[bytes];
        SECURE_RANDOM.nextBytes(buffer);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer);
    }

    /** 生成 32 字节随机字符串（适合做 JWT secret） */
    public static String generateJwtSecret() {
        return randomBase64(32);
    }
}
