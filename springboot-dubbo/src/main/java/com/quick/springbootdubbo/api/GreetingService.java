package com.quick.springbootdubbo.api;

/**
 * Dubbo 服务接口 —— 提供者与消费者之间的"契约"。
 * <p>
 * 提供者实现这个接口，消费者通过这个接口发起远程调用。
 * 在 Dubbo 体系中，接口定义通常独立为公共 jar 包，供上下游共同依赖。
 */
public interface GreetingService {

    /**
     * 打招呼。
     *
     * @param name 用户名称
     * @return 问候语
     */
    String sayHello(String name);

    /**
     * 获取当前提供者信息（含时间戳），方便在消费者侧验证每次 RPC 调用。
     *
     * @return 提供者信息字符串
     */
    String getServerInfo();
}
