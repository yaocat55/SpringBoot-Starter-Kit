package com.quick.timing;

import java.util.Map;

import org.springframework.aop.aspectj.AspectJExpressionPointcut;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.ClassUtils;

import com.quick.timing.annotation.EnableMethodTiming;

/**
 * ImportBeanDefinitionRegistrar 实现 —— 根据 @EnableMethodTiming 的属性，
 * 动态构建 AOP 切面并注册为 Spring Bean。
 *
 * 这是 @EnableXXX 模式的核心：注解只负责"标记 + 传参"，
 * Registrar 负责"读取参数 + 注册 BeanDefinition"。
 */
public class MethodTimingRegistrar
        implements ImportBeanDefinitionRegistrar, EnvironmentAware {

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void registerBeanDefinitions(AnnotationMetadata metadata,
                                        BeanDefinitionRegistry registry) {

        // ① 读取 @EnableMethodTiming 注解的属性值
        Map<String, Object> attrs = metadata
                .getAnnotationAttributes(EnableMethodTiming.class.getName());

        String[] basePackages = (String[]) attrs.get("basePackages");

        // ② 未指定包路径时，默认使用标注类所在的包
        if (basePackages.length == 0) {
            String className = metadata.getClassName();
            basePackages = new String[]{ClassUtils.getPackageName(className)};
        }

        // ③ 构建 AspectJ 切入点表达式
        //    例如: execution(* com.example.service..*.*(..))
        String pointcutExpression = buildPointcutExpression(basePackages);

        // ④ 注册 MethodTimingInterceptor Bean
        String interceptorBeanName = "methodTimingInterceptor";
        registry.registerBeanDefinition(interceptorBeanName,
                BeanDefinitionBuilder
                        .genericBeanDefinition(MethodTimingInterceptor.class)
                        .getBeanDefinition());

        // ⑤ 注册 AspectJExpressionPointcut Bean
        String pointcutBeanName = "methodTimingPointcut";
        BeanDefinition pointcutDef = BeanDefinitionBuilder
                .genericBeanDefinition(AspectJExpressionPointcut.class)
                .addPropertyValue("expression", pointcutExpression)
                .getBeanDefinition();
        registry.registerBeanDefinition(pointcutBeanName, pointcutDef);

        // ⑥ 注册 Advisor Bean（切面 = 切入点 + 拦截器）
        String advisorBeanName = "methodTimingAdvisor";
        BeanDefinition advisorDef = BeanDefinitionBuilder
                .genericBeanDefinition(DefaultPointcutAdvisor.class)
                .addConstructorArgReference(pointcutBeanName)
                .addConstructorArgReference(interceptorBeanName)
                .getBeanDefinition();
        registry.registerBeanDefinition(advisorBeanName, advisorDef);
    }

    /**
     * 将包路径数组拼接为 AspectJ pointcut 表达式。
     * "com.a, com.b" → "execution(* com.a..*.*(..)) || execution(* com.b..*.*(..))"
     */
    private String buildPointcutExpression(String[] basePackages) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < basePackages.length; i++) {
            if (i > 0) sb.append(" || ");
            sb.append("execution(* ")
              .append(basePackages[i])
              .append("..*.*(..))");
        }
        return sb.toString();
    }
}
