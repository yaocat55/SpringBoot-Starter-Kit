package com.quick.springbootmongdb;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

/**
 * MongoDB 演示应用。
 * <ul>
 *   <li>{@code @EnableMongoRepositories}：扫描 MongoRepository 接口并自动生成实现</li>
 *   <li>{@code @EnableMongoAuditing}：启用 {@code @CreatedDate / @LastModifiedDate} 自动填充</li>
 *   <li>未配置 MongoDB URI 时，内嵌 MongoDB 自动启动（零安装开箱即用）</li>
 * </ul>
 */
@SpringBootApplication
@EnableMongoRepositories
@EnableMongoAuditing
public class SpringbootMongdbApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringbootMongdbApplication.class, args);
    }
}
