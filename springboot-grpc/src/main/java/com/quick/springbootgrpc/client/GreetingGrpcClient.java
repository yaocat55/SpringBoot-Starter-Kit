package com.quick.springbootgrpc.client;

import com.quick.springbootgrpc.proto.GreetingProto;
import com.quick.springbootgrpc.proto.GreetingServiceGrpc;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

/**
 * gRPC 客户端 —— 下游。
 * <p>
 * {@code @GrpcClient("local-grpc-server")} 注入一个 BlockingStub，
 * 调用它的方法和调本地方法一样简单，底层是 HTTP/2 + Protobuf 二进制传输。
 * <p>
 * 注解值 {@code local-grpc-server} 对应 application.yml 中 grpc.client 下的 key。
 */
@Slf4j
@Component
public class GreetingGrpcClient {

    @GrpcClient("local-grpc-server")
    private GreetingServiceGrpc.GreetingServiceBlockingStub greetingStub;

    /**
     * 通过 gRPC 调用 sayHello（阻塞式，等待响应）。
     */
    public String callSayHello(String name) {
        log.info("[gRPC Client] 发起 gRPC 调用 sayHello, name={}", name);

        GreetingProto.HelloRequest request = GreetingProto.HelloRequest.newBuilder()
                .setName(name)
                .build();

        GreetingProto.HelloReply reply = greetingStub.sayHello(request);

        log.info("[gRPC Client] 收到响应: {}", reply.getMessage());
        return reply.getMessage();
    }

    /**
     * 通过 gRPC 获取服务端运行时信息。
     */
    public String callGetServerInfo() {
        log.info("[gRPC Client] 发起 gRPC 调用 getServerInfo");

        GreetingProto.ServerInfoRequest request = GreetingProto.ServerInfoRequest.newBuilder().build();

        GreetingProto.ServerInfoReply reply = greetingStub.getServerInfo(request);

        String info = String.format("thread=%s, timestamp=%s, java=%s",
                reply.getThreadName(), reply.getTimestamp(), reply.getJavaVersion());
        log.info("[gRPC Client] 收到响应: {}", info);
        return info;
    }
}
