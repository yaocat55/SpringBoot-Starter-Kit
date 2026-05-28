package com.quick.timing.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Import;

import com.quick.timing.MethodTimingRegistrar;

/**
 * 启用方法耗时日志。
 *
 * <pre>
 * &#064;Configuration
 * &#064;EnableMethodTiming(basePackages = "com.example.service")
 * public class AppConfig {
 * }
 * </pre>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Import(MethodTimingRegistrar.class)
public @interface EnableMethodTiming {

    /** 需要监控的包路径，不填则默认扫描标注类所在的包 */
    String[] basePackages() default {};
}
