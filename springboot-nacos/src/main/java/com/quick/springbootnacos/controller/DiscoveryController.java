package com.quick.springbootnacos.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Nacos 服务发现演示。
 * <p>
 * 概念：
 * <ul>
 *   <li>注册：应用启动后自动把 IP:端口 注册到 Nacos</li>
 *   <li>发现：通过服务名拿到所有实例的地址列表</li>
 *   <li>调用：RestTemplate + @LoadBalanced，直接用服务名代替 URL</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/discovery")
@RequiredArgsConstructor
public class DiscoveryController {

    private final DiscoveryClient discoveryClient;
    private final RestTemplate restTemplate;

    // ==================== 1. 查看注册信息 ====================

    /** 查看本服务自己的实例信息 */
    @GetMapping("/self")
    public List<ServiceInstance> self() {
        return discoveryClient.getInstances("nacos-demo");
    }

    /** 列出 Nacos 上所有已注册的服务名 */
    @GetMapping("/services")
    public List<String> services() {
        return discoveryClient.getServices();
    }

    /** 列出所有服务及其所有实例的地址 */
    @GetMapping("/services/detail")
    public Map<String, List<String>> serviceDetail() {
        return discoveryClient.getServices().stream()
                .collect(Collectors.toMap(
                        s -> s,
                        s -> discoveryClient.getInstances(s).stream()
                                .map(i -> i.getHost() + ":" + i.getPort())
                                .collect(Collectors.toList())
                ));
    }

    // ==================== 2. 服务间调用 ====================

    /**
     * 通过服务名调用另一个服务。
     * RestTemplate + @LoadBalanced 会自动把 "服务名" 解析为 "IP:端口"。
     *
     * <pre>
     * curl http://localhost:8080/api/discovery/call/nacos-demo/api/config/title
     * </pre>
     */
    @GetMapping("/call/{serviceName}/**")
    public Object callOtherService(@PathVariable String serviceName) {
        // Spring 自动把 nacos-demo 解析为实际 IP:端口
        String url = "http://" + serviceName + "/api/config";
        return restTemplate.getForObject(url, Object.class);
    }

    // ==================== 3. 健康检查（供 Nacos 探测） ====================

    @GetMapping("/hello")
    public String hello() {
        return "Hello from nacos-demo!";
    }
}
