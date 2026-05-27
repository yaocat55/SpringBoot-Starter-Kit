# SpringBoot Starter Kit

Spring Boot 3 日常开发组件模板合集，每个模块独立可运行，复制即用。

- **JDK** 17+
- **Spring Boot** 3.5.14
- **Maven** 多模块

## 已接入

### 消息队列

| 模块 | 说明 |
|------|------|
| [spring-rabbitmq](./spring-rabbitmq) | RabbitMQ 消息队列 |
| [springboot-kafka](./springboot-kafka) | Kafka 消息队列 |
| [springboot-rocketmq](./springboot-rocketmq) | RocketMQ 消息队列 |
| [springboot-aliyun-mqtt](./springboot-aliyun-mqtt) | 阿里云 MQTT 物联网通信 |
| [springboot-emqx](./springboot-emqx) | EMQX MQTT 5.0 客户端 |

### 缓存 & 数据库

| 模块 | 说明 |
|------|------|
| [springboot-redis](./springboot-redis) | Redis 缓存 |
| [springboot-caffeine](./springboot-caffeine) | Caffeine 本地缓存 |
| [springboot-mongdb](./springboot-mongdb) | MongoDB 集成 |
| [springboot-elasticsearch](./springboot-elasticsearch) | Elasticsearch 搜索引擎 |

### RPC & HTTP 客户端

| 模块 | 说明 |
|------|------|
| [springboot-dubbo](./springboot-dubbo) | Apache Dubbo RPC 调用 |
| [springboot-grpc](./springboot-grpc) | gRPC 集成 |
| [springboot-openfeign](./springboot-openfeign) | OpenFeign 声明式 HTTP 客户端 |

### 权限认证

| 模块 | 说明 |
|------|------|
| [springboot-security](./springboot-security) | Spring Security 权限认证 |
| [springboot-satoken](./springboot-satoken) | Sa-Token 轻量级权限认证 |

### 微服务治理

| 模块 | 说明 |
|------|------|
| [springboot-nacos](./springboot-nacos) | Nacos 配置中心 & 服务发现 |
| [springboot-sentinel](./springboot-sentinel) | Sentinel 限流/熔断/降级 |
| [springboot-dynamictp](./springboot-dynamictp) | 动态线程池管理 |
| [springboot-xxljob](./springboot-xxljob) | XXL-JOB 分布式任务调度 |

### 基础组件

| 模块 | 说明 |
|------|------|
| [springboot-apiresult](./springboot-apiresult) | 统一 API 响应模板 |
| [springboot-exception](./springboot-exception) | 全局异常处理 |
| [springboot-lombok](./springboot-lombok) | Lombok 注解演示 |

## 使用

```bash
# 启动某个模块
mvn spring-boot:run -pl springboot-redis

# 全部编译
mvn clean compile
```

## 待接入

- [ ] MinIO
