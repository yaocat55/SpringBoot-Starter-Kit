package com.quick.springbootsecurity.security;

import com.quick.springbootsecurity.model.LoginUser;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * JWT 令牌工具类 —— 生成、解析、验证 Access Token 与 Refresh Token。
 * <p>
 * 两种 Token：
 * <ul>
 *   <li><b>Access Token</b>：短期（2h），携带用户身份与权限，每次请求携带</li>
 *   <li><b>Refresh Token</b>：长期（14d），仅用于换取新的 Access Token，不携带权限</li>
 * </ul>
 */
@Slf4j
@Component
public class JwtTokenProvider {

    private final SecretKey secretKey;
    private final long accessTokenExpiration;
    private final long refreshTokenExpiration;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiration:7200000}") long accessTokenExpiration,
            @Value("${jwt.refresh-token-expiration:1209600000}") long refreshTokenExpiration) {
        this.secretKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(
                java.util.Base64.getEncoder().encodeToString(secret.getBytes())
        ));
        this.accessTokenExpiration = accessTokenExpiration;
        this.refreshTokenExpiration = refreshTokenExpiration;
    }

    // ==================== 生成 Token ====================

    /** 基于 LoginUser 生成 Access Token */
    public String generateAccessToken(LoginUser loginUser) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", loginUser.getUserId());
        claims.put("username", loginUser.getUsername());
        claims.put("roles", loginUser.getRoles());
        claims.put("permissions", loginUser.getPermissions());
        return buildToken(claims, String.valueOf(loginUser.getUserId()), accessTokenExpiration);
    }

    /** 生成 Refresh Token（不含业务信息，仅用于刷新） */
    public String generateRefreshToken(LoginUser loginUser) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", loginUser.getUserId());
        claims.put("tokenType", "refresh");
        return buildToken(claims, "refresh:" + loginUser.getUserId(), refreshTokenExpiration);
    }

    private String buildToken(Map<String, Object> claims, String subject, long expiration) {
        Date now = new Date();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .claims(claims)
                .subject(subject)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expiration))
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }

    // ==================== 解析 Token ====================

    /** 从 Token 中提取 Claims */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** 从 Token 中提取用户名 */
    public String getUsername(String token) {
        return parseToken(token).get("username", String.class);
    }

    /** 从 Token 中提取用户 ID */
    public Long getUserId(String token) {
        return parseToken(token).get("userId", Long.class);
    }

    // ==================== 验证 Token ====================

    /** 验证 Token 是否有效（不抛异常即为有效） */
    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (SecurityException | MalformedJwtException e) {
            log.warn("无效的 JWT 签名: {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            log.warn("JWT 已过期: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.warn("不支持的 JWT 格式: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("JWT Token 为空: {}", e.getMessage());
        }
        return false;
    }

    /** 判断 Token 是否已过期 */
    public boolean isExpired(String token) {
        try {
            return parseToken(token).getExpiration().before(new Date());
        } catch (ExpiredJwtException e) {
            return true;
        }
    }

    /** 获取 Token 剩余有效时间（毫秒） */
    public long getRemainingTime(String token) {
        try {
            Date expiration = parseToken(token).getExpiration();
            return expiration.getTime() - System.currentTimeMillis();
        } catch (ExpiredJwtException e) {
            return 0;
        }
    }
}
