# springboot-grpc

SpringBoot gRPC 接入模板，一个应用同时演示 **gRPC Server（服务端）**和 **gRPC Client（客户端）**，开箱即用。

## 目录

- [是什么](#是什么)
- [为什么需要 gRPC](#为什么需要-grpc)
- [核心概念——5 分钟理解 gRPC](#核心概念--5-分钟理解-grpc)
- [快速接入](#快速接入)
- [项目结构](#项目结构)
- [调用链](#调用链)
- [代码导读——从 0 到 1 写一个 gRPC 服务](#代码导读--从-0-到-1-写一个-grpc-服务)
- [gRPC vs REST vs Dubbo](#grpc-vs-rest-vs-dubbo)
- [生产环境接入指南](#生产环境接入指南)
- [常见问题](#常见问题)

## 是什么

**gRPC** 是 Google 开源的高性能 RPC 框架，现在是 CNCF 毕业项目。

它有三件"武器"让你无法忽略：

| 武器 | 干什么的 | 为什么重要 |
|------|---------|-----------|
| **Protocol Buffers** | 接口定义语言（IDL），用 `.proto` 文件描述服务 | 强类型契约，编译期检查，自带文档 |
| **HTTP/2** | 传输协议 | 多路复用、双向流、头部压缩，性能远超 HTTP/1.1 |
| **代码生成** | `.proto` → Java/Python/Go/... 的客户端/服务端代码 | 不用手写序列化、网络层，编译器直接生成 |

一句话：**gRPC 让你用 IDL 定义接口，编译器自动生成客户端和服务端代码，底层走 HTTP/2 + Protobuf 二进制传输。**

## 为什么需要 gRPC

### 和 REST 对比

```java
// ===== REST 方式（HTTP + JSON）=====
// 请求
POST /api/user/getById
Content-Type: application/json
{"userId": 123}

// 代码里
String json = objectMapper.writeValueAsString(request);  // 手动序列化
HttpResponse resp = httpClient.post(url, json);           // HTTP 调用
User user = objectMapper.readValue(resp.body(), User.class); // 手动反序列化

// ===== gRPC 方式 =====
User user = userStub.getUserById(
    GetUserRequest.newBuilder().setUserId(123).build()
);
// 序列化？编译器生成的。HTTP 调用？Stub 内置的。类型安全？编译期保证。
```

| 维度 | REST (HTTP/JSON) | gRPC |
|------|-----------------|------|
| 接口定义 | 口头约定 / Swagger 文档 | `.proto` 文件，编译期强类型 |
| 序列化 | JSON 文本，体积大 | Protobuf 二进制，体积小 3-10 倍 |
| 传输协议 | HTTP/1.1 | HTTP/2（多路复用、流式传输） |
| 代码生成 | 需要 Swagger Codegen | protoc 编译器直接生成 |
| 流式调用 | 不支持 | 原生支持（客户端流、服务端流、双向流） |
| 浏览器支持 | 原生支持 | 需 grpc-web 代理 |
| 适用场景 | 对外 API、浏览器直接调用 | 内部微服务高性能调用 |

**一句话：REST 给外部用，gRPC 给内部微服务之间用。**

## 核心概念 —— 5 分钟理解 gRPC

### 工作流程

```
① 写 .proto 文件（接口定义）
         │
         ▼
② protoc 编译器生成代码
   ├── GreetingServiceGrpc.java    ← 服务端骨架 + 客户端 Stub
   └── GreetingProto.java          ← 请求/响应消息类
         │
         ├──────────────────────────┐
         ▼                          ▼
③ 服务端实现                       ④ 客户端调用
   extends ImplBase                 使用 Stub
   @GrpcService                     @GrpcClient
```

### 架构图

```
┌──────────────────────────────────────────────┐
│                  .proto 文件                   │
│   service GreetingService {                   │
│     rpc SayHello(HelloRequest)                │
│       returns (HelloReply);                   │
│   }                                           │
└────────────┬───────────────────┬─────────────┘
             │                   │
      protoc 编译器         protoc 编译器
             │                   │
             ▼                   ▼
┌────────────────────┐  ┌─────────────────────┐
│   服务端（Server）    │  │   客户端（Client）    │
│                    │  │                     │
│  @GrpcService      │  │  @GrpcClient        │
│  extends ImplBase  │  │  BlockingStub       │
│                    │  │                     │
│  实现业务逻辑       │  │  stub.sayHello()    │
│  监听 9090 端口    │  │  → HTTP/2 请求      │
└────────┬───────────┘  └──────────┬──────────┘
         │                         │
         │    HTTP/2 + Protobuf    │
         └─────────────────────────┘
```

### 4 种调用模式

| 模式 | 说明 | 示例 |
|------|------|------|
| **Unary**（一问一答） | 客户端发一个请求，服务端回一个响应 | 查询用户、下订单 |
| **Server Streaming** | 客户端发一个请求，服务端持续推送多条响应 | 订阅行情、日志推送 |
| **Client Streaming** | 客户端持续发送多条请求，服务端汇总后回一个响应 | 批量上传、日志上报 |
| **Bidirectional** | 双方都可以独立发送消息，互不阻塞 | 聊天、实时协作 |

本示例演示最常用的 **Unary** 模式。掌握 Unary 之后，其他三种模式只是 `StreamObserver` 的用法变化。

## 快速接入

### 1. 添加依赖和插件

```xml
<dependencies>
    <!-- gRPC Spring Boot Starter（3.x 版本兼容 Spring Boot 3.x） -->
    <dependency>
        <groupId>net.devh</groupId>
        <artifactId>grpc-spring-boot-starter</artifactId>
        <version>3.1.0.RELEASE</version>
    </dependency>
    <!-- protoc 生成的代码用到 @javax.annotation.Generated -->
    <dependency>
        <groupId>javax.annotation</groupId>
        <artifactId>javax.annotation-api</artifactId>
        <version>1.3.2</version>
    </dependency>
</dependencies>

<build>
    <extensions>
        <extension>
            <groupId>kr.motd.maven</groupId>
            <artifactId>os-maven-plugin</artifactId>
            <version>1.7.1</version>
        </extension>
    </extensions>
    <plugins>
        <plugin>
            <groupId>org.xolstice.maven.plugins</groupId>
            <artifactId>protobuf-maven-plugin</artifactId>
            <version>0.6.1</version>
            <configuration>
                <protocArtifact>com.google.protobuf:protoc:3.25.1:exe:${os.detected.classifier}</protocArtifact>
                <pluginId>grpc-java</pluginId>
                <pluginArtifact>io.grpc:protoc-gen-grpc-java:1.61.0:exe:${os.detected.classifier}</pluginArtifact>
            </configuration>
            <executions>
                <execution>
                    <goals><goal>compile</goal><goal>compile-custom</goal></goals>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

### 2. 写 .proto 文件

```protobuf
// src/main/proto/greeting.proto
syntax = "proto3";

option java_package = "com.example.proto";
option java_outer_classname = "GreetingProto";

service GreetingService {
  rpc SayHello (HelloRequest) returns (HelloReply);
}

message HelloRequest {
  string name = 1;    // =1 是字段编号（不是值），每个字段唯一
}

message HelloReply {
  string message = 1;
}
```

### 3. 配置 application.yml

```yml
grpc:
  server:
    port: 9090               # gRPC 服务端端口
  client:
    local-grpc-server:       # key = @GrpcClient 注解的值
      address: static://localhost:9090
      negotiation-type: plaintext
```

### 4. 编写服务端（上游）

```java
@GrpcService  // ← 注册为 gRPC 服务
public class GreetingGrpcService extends GreetingServiceGrpc.GreetingServiceImplBase {

    @Override
    public void sayHello(HelloRequest request, StreamObserver<HelloReply> responseObserver) {
        HelloReply reply = HelloReply.newBuilder()
                .setMessage("Hello " + request.getName() + "!")
                .build();
        responseObserver.onNext(reply);     // 发送响应
        responseObserver.onCompleted();     // 本次 RPC 结束
    }
}
```

### 5. 编写客户端（下游）

```java
@Component
public class GreetingGrpcClient {
    @GrpcClient("local-grpc-server")  // ← 注入 Stub，值对应 yml 中 client 的 key
    private GreetingServiceGrpc.GreetingServiceBlockingStub stub;

    public String callSayHello(String name) {
        HelloRequest req = HelloRequest.newBuilder().setName(name).build();
        HelloReply reply = stub.sayHello(req);   // 远程调用，阻塞等待响应
        return reply.getMessage();
    }
}
```

### 6. 启动验证

```bash
cd springboot-grpc
mvn clean compile           # 先编译（会自动生成 proto 的 Java 代码）
mvn spring-boot:run
```

```bash
# 通过 Client 封装调用
curl "http://localhost:8085/api/grpc/client/hello?name=gRPC"

# 直接 Stub 调用
curl "http://localhost:8085/api/grpc/direct/hello?name=gRPC"

# 获取 gRPC Server 信息
curl "http://localhost:8085/api/grpc/client/info"

# 对比两种调用方式
curl "http://localhost:8085/api/grpc/compare?name=gRPC"
```

## 项目结构

```
springboot-grpc/
├── pom.xml                                    # grpc-spring-boot-starter + protobuf-maven-plugin
├── README.md
└── src/main/
    ├── proto/
    │   └── greeting.proto                     # 【IDL】gRPC 服务定义（方法 + 消息结构）
    ├── java/com/quick/springbootgrpc/
    │   ├── SpringbootGrpcApplication.java     # 启动类（无需额外注解）
    │   ├── server/
    │   │   └── GreetingGrpcService.java       # 【服务端】@GrpcService 暴露服务
    │   ├── client/
    │   │   └── GreetingGrpcClient.java        # 【客户端】@GrpcClient 注入 Stub
    │   └── controller/
    │       └── GreetingController.java        # REST 接口，验证 gRPC 调用链路
    └── resources/
        └── application.yml                    # gRPC server 端口 + client 目标地址
```

编译后会在 `target/generated-sources/protobuf/` 下生成：
- `GreetingProto.java` —— 消息类（HelloRequest, HelloReply, ServerInfoRequest, ServerInfoReply）
- `GreetingServiceGrpc.java` —— gRPC 骨架（`GreetingServiceImplBase` + `GreetingServiceBlockingStub`）

## 调用链

### 请求链路全景图

```
浏览器 / curl
    │
    ▼
GreetingController                      ← REST 入口 @ /api/grpc/*
    │
    ├── GET /client/hello
    │       │
    │       ▼
    │   GreetingGrpcClient.callSayHello()          ← @Component 封装层
    │       │
    │       ▼
    │   greetingStub.sayHello(request)              ← @GrpcClient 注入的 Stub
    │       │                                         （gRPC 框架生成的代理）
    │       │
    │       ├── Proto 序列化 HelloRequest → 二进制字节
    │       ├── 通过 HTTP/2 连接发送到 localhost:9090
    │       │       │
    │       │       ▼
    │       │   GreetingGrpcService.sayHello()     ← @GrpcService，gRPC Server
    │       │       │
    │       │       ├── 反序列化请求
    │       │       ├── 执行业务逻辑
    │       │       ├── 构造 HelloReply
    │       │       └── onNext(reply) + onCompleted()
    │       │
    │       ├── 收到 HTTP/2 响应
    │       ├── Proto 反序列化 → HelloReply
    │       └── 返回 reply.getMessage() → Controller → 浏览器
    │
    ├── GET /direct/hello
    │       └── Controller 内直接 @GrpcClient Stub 调用
    │           （跳过 Client 封装层，直接走 gRPC）
    │
    └── GET /compare
            └── 同时走两条路径，验证结果一致
```

### 端口说明

```
localhost:8085   ← REST（Spring MVC，浏览器直接访问）
localhost:9090   ← gRPC（HTTP/2 + Protobuf，Stub 内部连接）
```

## 代码导读 —— 从 0 到 1 写一个 gRPC 服务

### 第 1 步：定义 .proto（写合同）

```protobuf
// greeting.proto —— 这是 gRPC 世界的"接口文档"和"类型定义"
syntax = "proto3";

service GreetingService {
  rpc SayHello (HelloRequest) returns (HelloReply);
}

message HelloRequest { string name = 1; }   // 字段后的数字是编号，不是值
message HelloReply   { string message = 1; }
```

### 第 2 步：编译 .proto → Maven 自动生成 Java 代码

```bash
mvn compile
# target/generated-sources/protobuf/ 下会多出 .java 文件
# 这个过程完全是自动的，开发者不用碰生成代码
```

### 第 3 步：实现服务端

```java
@GrpcService  // 告诉 gRPC："这是一个服务实现，请注册"
public class GreetingGrpcService extends GreetingServiceGrpc.GreetingServiceImplBase {
    @Override
    public void sayHello(HelloRequest request, StreamObserver<HelloReply> responseObserver) {
        // 1. 构造响应（Builder 模式，proto 自动生成）
        HelloReply reply = HelloReply.newBuilder()
                .setMessage("Hello " + request.getName())
                .build();
        // 2. 发送响应
        responseObserver.onNext(reply);
        // 3. 标记本次 RPC 完成
        responseObserver.onCompleted();
    }
}
```

### 第 4 步：编写客户端

```java
@Component
public class GreetingGrpcClient {
    @GrpcClient("local-grpc-server")  // 值为 yml 中 grpc.client.{key}
    private GreetingServiceGrpc.GreetingServiceBlockingStub stub;
    //  ↑ 这是 gRPC 生成的代理类，调用它 = 发起远程调用

    public String sayHello(String name) {
        HelloRequest req = HelloRequest.newBuilder().setName(name).build();
        HelloReply reply = stub.sayHello(req);  // 远程调用！但写起来像本地方法
        return reply.getMessage();
    }
}
```

### 第 5 步：通过 REST 暴露验证

```java
@RestController
public class GreetingController {
    private final GreetingGrpcClient grpcClient;

    @GetMapping("/hello")
    public Map<String, Object> hello(@RequestParam String name) {
        return Map.of("result", grpcClient.sayHello(name));
    }
}
```

## gRPC vs REST vs Dubbo

| 维度 | REST | Dubbo | gRPC |
|------|------|-------|------|
| **接口定义** | OpenAPI/Swagger 文档 | Java Interface | .proto IDL 文件 |
| **序列化** | JSON（文本，大） | Hessian/Java 原生 | Protobuf（二进制，小） |
| **传输协议** | HTTP/1.1 | TCP 长连接 | HTTP/2 |
| **流式调用** | 不支持（需 WebSocket） | 不支持 | 原生支持 4 种流模式 |
| **跨语言** | 任何语言 | 主要 Java | C++/Java/Go/Python/... 官方支持 |
| **浏览器支持** | 原生 | 不支持 | 需 grpc-web 代理 |
| **最佳场景** | 对外 API、BFF 层 | Java 内部微服务 | 多语言微服务、低延迟场景 |

## 生产环境接入指南

### 1. 启用 TLS

本示例使用 `plaintext`（明文），生产必须加密：

```yml
grpc:
  client:
    my-server:
      negotiation-type: TLS
      security:
        certificate-chain: file:client.crt
        private-key: file:client.key
        trust-certificate-collection: file:ca.crt
```

### 2. 服务发现（替代 static://）

本示例使用 `static://localhost:9090` 直连，仅适合本地开发。生产环境统一使用 Nacos 做注册中心：

```xml
<!-- gRPC + Nacos 服务发现 -->
<dependency>
    <groupId>net.devh</groupId>
    <artifactId>grpc-spring-boot-starter</artifactId>
    <version>3.1.0.RELEASE</version>
</dependency>
<dependency>
    <groupId>com.alibaba.nacos</groupId>
    <artifactId>nacos-client</artifactId>
    <version>2.4.3</version>
</dependency>
```

```yml
grpc:
  client:
    user-service:
      address: discovery:///user-service   # Nacos 自动发现，无需 hardcode IP
```

gRPC Server 启动后会自动注册到 Nacos，Client 通过服务名即可发现并调用，上下线自动感知。

### 3. 拦截器（鉴权、日志、链路追踪）

```java
@Component
public class AuthInterceptor implements ServerInterceptor {
    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call, Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {
        String token = headers.get(Metadata.Key.of("Authorization",
                Metadata.ASCII_STRING_MARSHALLER));
        if (!isValid(token)) {
            call.close(Status.UNAUTHENTICATED, new Metadata());
            return new ServerCall.Listener<>() {};
        }
        return next.startCall(call, headers);
    }
}
```

### 4. 生产参数建议

| 参数 | 建议值 | 原因 |
|------|--------|------|
| `grpc.server.port` | 9090（或 443 for TLS） | 不要和 REST 8080 共用一个端口 |
| 连接池 | 默认即长连接复用 | HTTP/2 自动多路复用，不需要连接池 |
| Keepalive | `grpc.server.keep-alive-time=60s` | 防止负载均衡器断开连接 |
| 超时 | `stub.withDeadlineAfter(3, SECONDS)` | 每个调用都应设 deadline |

### 5. proto 文件管理

在实际项目中，`.proto` 文件通常放在独立的仓库（如 `api-proto`），供所有语言的服务端和客户端共同依赖：

```
my-project/
├── api-proto/               ← Git 子模块 / 独立仓库，多语言共享
│   └── src/main/proto/
│       ├── greeting.proto
│       └── user.proto
├── user-service/            ← Java 实现
├── order-service/           ← Java 实现
└── billing-service/         ← Go 实现（同一个 .proto 生成 Go 代码）
```

## 常见问题

**Q: gRPC 和 Spring Boot 怎么配合？**
`grpc-spring-boot-starter` 把 gRPC Server/Client 变成了 Spring 管理的组件。`@GrpcService` 和 `@GrpcClient` 都是 Spring Bean，可以用 `@Autowired`（但 `@GrpcClient` 不推荐和 `@Autowired` 混用，直接 `@GrpcClient` 就够了）。

**Q: 编译时报 "找不到 GreetingServiceGrpc"？**
还没编译 .proto 文件。先运行 `mvn compile`，protobuf-maven-plugin 会自动生成代码到 `target/generated-sources/protobuf/`。IDE 需要把这个目录标记为 Generated Sources。

**Q: 客户端调用报 "UNAVAILABLE"？**
检查：1) gRPC Server 是否启动 2) `grpc.client.*.address` 端口是否正确 3) `negotiation-type` 是否匹配（明文 vs TLS）。

**Q: 为什么不用 @EnableGrpc？**
`grpc-spring-boot-starter` 3.x 不再需要这个注解。Spring Boot 自动配置已经接管了 gRPC Server/Client 的启动。

**Q: BlockingStub 和 Stub 什么区别？**
- `BlockingStub`：同步阻塞，调用 `sayHello()` 后一直等到服务端返回，最简单
- `Stub`（异步）：返回 `StreamObserver`，响应通过回调处理，适合高并发
- `FutureStub`：返回 `ListenableFuture`，适合配合 CompletableFuture 做编排

本示例用 `BlockingStub`，最简单直观。生产环境可以根据并发需求选择异步 Stub。

**Q: proto 文件里 `= 1` 是什么意思？**
字段编号，不是默认值。Protobuf 序列化时用编号而不是字段名，所以编号不能随便改（改了读不到旧数据）。新增字段必须用新编号。
