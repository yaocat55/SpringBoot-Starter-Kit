package com.quick.timing;

import java.lang.reflect.Method;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

/**
 * AOP 方法拦截器 —— 记录每个方法调用的耗时。
 */
public class MethodTimingInterceptor implements MethodInterceptor {

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        long start = System.currentTimeMillis();
        try {
            return invocation.proceed();
        } finally {
            long elapsed = System.currentTimeMillis() - start;
            Method method = invocation.getMethod();
            String className = method.getDeclaringClass().getSimpleName();
            System.out.printf("[TIMING] %s.%s() → %dms%n", className, method.getName(), elapsed);
        }
    }
}
