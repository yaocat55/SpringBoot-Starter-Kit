package com.quick.springbootgrpc.controller;

import com.quick.springbootgrpc.client.GreetingGrpcClient;
import com.quick.springbootgrpc.proto.GreetingProto;
import com.quick.springbootgrpc.proto.GreetingServiceGrpc;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST 控制器 —— 验证 gRPC 调用链路。
 * <p>
 * 提供两种调用方式便于对比：
 * <ul>
 *   <li>通过 {@link GreetingGrpcClient} 封装调用（推荐生产使用）</li>
 *   <li>通过 {@code @GrpcClient} 直接注入 stub 调用（零封装，演示用）</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/grpc")
@RequiredArgsConstructor
public class GreetingController {

    private final GreetingGrpcClient grpcClient;

    /** 直接注入 stub（跳过了 Client 封装层，直接发 gRPC 请求） */
    @GrpcClient("local-grpc-server")
    private GreetingServiceGrpc.GreetingServiceBlockingStub directStub;

    // ======================== 通过 Client 封装调用 ========================

    @GetMapping("/client/hello")
    public Map<String, Object> clientHello(@RequestParam(defaultValue = "gRPC") String name) {
        String result = grpcClient.callSayHello(name);
        return Map.of("success", true, "result", result, "caller", "GreetingGrpcClient");
    }

    @GetMapping("/client/info")
    public Map<String, Object> clientInfo() {
        String result = grpcClient.callGetServerInfo();
        return Map.of("success", true, "result", result, "caller", "GreetingGrpcClient");
    }

    // ======================== 直接 Stub 调用 ========================

    @GetMapping("/direct/hello")
    public Map<String, Object> directHello(@RequestParam(defaultValue = "gRPC") String name) {
        GreetingProto.HelloRequest request = GreetingProto.HelloRequest.newBuilder()
                .setName(name)
                .build();
        GreetingProto.HelloReply reply = directStub.sayHello(request);
        log.info("[Controller 直接调用] sayHello result={}", reply.getMessage());
        return Map.of("success", true, "result", reply.getMessage(), "caller", "@GrpcClient stub");
    }

    @GetMapping("/direct/info")
    public Map<String, Object> directInfo() {
        GreetingProto.ServerInfoRequest request = GreetingProto.ServerInfoRequest.newBuilder().build();
        GreetingProto.ServerInfoReply reply = directStub.getServerInfo(request);
        String info = String.format("thread=%s, timestamp=%s, java=%s",
                reply.getThreadName(), reply.getTimestamp(), reply.getJavaVersion());
        log.info("[Controller 直接调用] getServerInfo result={}", info);
        return Map.of("success", true, "result", info, "caller", "@GrpcClient stub");
    }

    // ======================== 对比测试 ========================

    @GetMapping("/compare")
    public Map<String, Object> compare(@RequestParam(defaultValue = "gRPC") String name) {
        String viaClient = grpcClient.callSayHello(name);

        GreetingProto.HelloRequest request = GreetingProto.HelloRequest.newBuilder()
                .setName(name + "-direct").build();
        String viaDirect = directStub.sayHello(request).getMessage();

        return Map.of("success", true,
                "viaClient", viaClient,
                "viaDirect", viaDirect,
                "note", "两种调用方式最终走同一个 gRPC 服务端");
    }
}
