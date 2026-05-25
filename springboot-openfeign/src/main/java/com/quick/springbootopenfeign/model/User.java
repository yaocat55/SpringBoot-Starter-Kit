package com.quick.springbootopenfeign.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户实体 —— 简单 POJO，Feign 自动用 JSON 序列化/反序列化。
 * <p>
 * 和 Dubbo（需要 Java 序列化）、gRPC（需要 Protobuf）不同，
 * OpenFeign 走的 HTTP + JSON，不需要特殊的序列化定义。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {

    private Long id;
    private String name;
    private String email;

    /** 快速构造方法，方便演示 */
    public static User of(Long id, String name, String email) {
        return new User(id, name, email);
    }
}
