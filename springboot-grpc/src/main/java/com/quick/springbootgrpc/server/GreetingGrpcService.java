package com.quick.springbootgrpc.server;

import com.quick.springbootgrpc.proto.GreetingProto;
import com.quick.springbootgrpc.proto.GreetingServiceGrpc;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * gRPC 服务端 —— 上游。
 * <p>
 * 继承 .proto 编译生成的 ImplBase，用 {@code @GrpcService} 注解注册为 gRPC 服务。
 * gRPC Server 会自动扫描此类，绑定到配置的端口（application.yml 中 grpc.server.port=9090）。
 */
@Slf4j
@GrpcService
public class GreetingGrpcService extends GreetingServiceGrpc.GreetingServiceImplBase {

    /**
     * 一问一答（Unary RPC）：客户端发一个 HelloRequest，服务端回一个 HelloReply。
     */
    @Override
    public void sayHello(GreetingProto.HelloRequest request,
                         StreamObserver<GreetingProto.HelloReply> responseObserver) {
        log.info("[gRPC Server] 收到 sayHello 请求, name={}", request.getName());

        String message = String.format("Hello %s, from gRPC Server!", request.getName());
        GreetingProto.HelloReply reply = GreetingProto.HelloReply.newBuilder()
                .setMessage(message)
                .build();

        // onNext 发送响应，onCompleted 表示本次 RPC 完成
        responseObserver.onNext(reply);
        responseObserver.onCompleted();
        log.info("[gRPC Server] sayHello 响应完成: {}", message);
    }

    /**
     * 获取服务端运行时信息（线程名 + 时间戳 + Java 版本）。
     */
    @Override
    public void getServerInfo(GreetingProto.ServerInfoRequest request,
                              StreamObserver<GreetingProto.ServerInfoReply> responseObserver) {
        log.info("[gRPC Server] 收到 getServerInfo 请求");

        GreetingProto.ServerInfoReply reply = GreetingProto.ServerInfoReply.newBuilder()
                .setThreadName(Thread.currentThread().getName())
                .setTimestamp(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                .setJavaVersion(System.getProperty("java.version"))
                .build();

        responseObserver.onNext(reply);
        responseObserver.onCompleted();
        log.info("[gRPC Server] getServerInfo 响应完成");
    }
}
