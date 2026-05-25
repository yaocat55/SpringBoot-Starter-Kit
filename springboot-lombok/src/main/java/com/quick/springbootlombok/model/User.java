package com.quick.springbootlombok.model;

import lombok.*;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * User 实体 —— 集中演示 @Data @Builder @Accessors(chain) 等常用注解。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class User {

    private Long id;

    @NonNull
    private String username;

    @NonNull
    private String email;

    private Integer age;

    @Builder.Default
    private Boolean active = true;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @ToString.Exclude
    private String password;

    @Getter(AccessLevel.NONE)
    private String internalCode;
}
