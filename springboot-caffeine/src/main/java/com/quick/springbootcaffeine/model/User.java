package com.quick.springbootcaffeine.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 用户实体 —— 缓存对象必须实现 Serializable（Caffeine 本地缓存非必须，
 * 但 Redis 等分布式缓存要求序列化，养成习惯总没错）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User implements Serializable {

    private Long id;
    private String name;
    private String email;
    private Integer age;

    public static User of(Long id, String name, String email, Integer age) {
        return new User(id, name, email, age);
    }
}
