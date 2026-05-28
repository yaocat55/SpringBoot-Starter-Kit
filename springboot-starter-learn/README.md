# 手把手封装一个 `@EnableXXX(xx=yy)` 格式的 Spring Boot Starter

> 以一个极简的 **方法耗时日志** starter 为例，演示 `@EnableXXX` + `ImportBeanDefinitionRegistrar` 的标准封装套路。

---

## 一句话说清这个 Starter 做了什么

在你的 `@Configuration` 类上写：

```java
@EnableMethodTiming(basePackages = "com.example.service")
```

之后 `com.example.service` 包下所有 public 方法的调用都会被自动拦截，打印执行耗时：

```
[TIMING] UserService.findById() → 23ms
[TIMING] OrderService.createOrder() → 156ms
```

---

## 目录

1. [终极目标：4 个文件搞定一个 Starter](#1-终极目标4-个文件搞定一个-starter)
2. [Step 1：写注解 `@EnableMethodTiming`](#2-step-1写注解-enablemethodtiming)
3. [Step 2：写拦截器 `MethodTimingInterceptor`](#3-step-2写拦截器-methodtiminginterceptor)
4. [Step 3：写注册器 `MethodTimingRegistrar`（核心）](#4-step-3写注册器-methodtimingregistrar核心)
5. [Step 4：写 POM](#5-step-4写-pom)
6. [完整调用链路图](#6-完整调用链路图)
7. [如何使用（3 步接入）](#7-如何使用3-步接入)
8. [对比：`@EnableXXX` 模式 vs 我之前写的 MyBatis Starter](#8-对比enablexxx-模式-vs-我之前写的-mybatis-starter)
9. [为什么要这样设计？](#9-为什么要这样设计)
10. [`ImportBeanDefinitionRegistrar` 的必须要做的事](#10-importbeandefinitionregistrar-的必须要做的事)

---

## 1. 终极目标：4 个文件搞定一个 Starter

```
springboot-starter-learn/
├── pom.xml                                    # POM（依赖清单）
└── src/main/java/com/quick/timing/
    ├── annotation/
    │   └── EnableMethodTiming.java            # ① @EnableXXX 注解
    ├── MethodTimingInterceptor.java           # ② AOP 拦截器（干活的）
    └── MethodTimingRegistrar.java             # ③ 注册器（核心）
```

> 就只有 3 个 Java 文件 + 1 个 POM。没有 FactoryBean、没有 AutoConfiguration、没有 spring.factories。这就是 `@EnableXXX` 模式的极简之美。

---

## 2. Step 1：写注解 `@EnableMethodTiming`

```java
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)               // 只能放在类上
@Import(MethodTimingRegistrar.class)    // ← 关键！@Import 触发 Register
public @interface EnableMethodTiming {

    /** 需要监控的包路径 */
    String[] basePackages() default {};  // ← 用户传参的地方
}
```

**三个关键点：**

| 要素 | 说明 |
|------|------|
| `@Target(ElementType.TYPE)` | 只能放在类/接口上，用户必须写到 `@Configuration` 类 |
| `@Import(MethodTimingRegistrar.class)` | Spring 扫描到这个注解时，自动执行 `MethodTimingRegistrar.registerBeanDefinitions()` |
| `basePackages()` 属性 | 这就是 `@EnableXXX(xx=yy)` 中的 `(xx=yy)` 部分 |

---

## 3. Step 2：写拦截器 `MethodTimingInterceptor`

```java
public class MethodTimingInterceptor implements MethodInterceptor {

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        long start = System.currentTimeMillis();
        try {
            return invocation.proceed();  // 执行真实方法
        } finally {
            long elapsed = System.currentTimeMillis() - start;
            String className = invocation.getMethod().getDeclaringClass().getSimpleName();
            System.out.printf("[TIMING] %s.%s() → %dms%n",
                    className, invocation.getMethod().getName(), elapsed);
        }
    }
}
```

这个类就是"具体干活的"。它实现了 `org.aopalliance.intercept.MethodInterceptor`（Spring AOP 的标准接口），在目标方法执行前后做计时。

---

## 4. Step 3：写注册器 `MethodTimingRegistrar`（核心）

这是整个 Starter 的**灵魂**。它实现了 `ImportBeanDefinitionRegistrar`：

```java
public class MethodTimingRegistrar
        implements ImportBeanDefinitionRegistrar, EnvironmentAware {

    @Override
    public void registerBeanDefinitions(AnnotationMetadata metadata,
                                        BeanDefinitionRegistry registry) {

        // ──── 第 1 步：读取注解参数 ────
        Map<String, Object> attrs = metadata
                .getAnnotationAttributes(EnableMethodTiming.class.getName());
        String[] basePackages = (String[]) attrs.get("basePackages");

        // 没填包路径 → 默认用标注类所在的包
        if (basePackages.length == 0) {
            String className = metadata.getClassName();
            basePackages = new String[]{ClassUtils.getPackageName(className)};
        }

        // ──── 第 2 步：构建 AspectJ 切入点表达式 ────
        // "com.a.service" → "execution(* com.a.service..*.*(..))"
        String expression = buildPointcutExpression(basePackages);

        // ──── 第 3 步：注册 Interceptor Bean ────
        registry.registerBeanDefinition("methodTimingInterceptor",
                BeanDefinitionBuilder
                        .genericBeanDefinition(MethodTimingInterceptor.class)
                        .getBeanDefinition());

        // ──── 第 4 步：注册 Pointcut Bean ────
        BeanDefinition pointcutDef = BeanDefinitionBuilder
                .genericBeanDefinition(AspectJExpressionPointcut.class)
                .addPropertyValue("expression", expression)    // 注入切入点表达式
                .getBeanDefinition();
        registry.registerBeanDefinition("methodTimingPointcut", pointcutDef);

        // ──── 第 5 步：注册 Advisor Bean（切面 = 切入点 + 拦截器） ────
        BeanDefinition advisorDef = BeanDefinitionBuilder
                .genericBeanDefinition(DefaultPointcutAdvisor.class)
                .addConstructorArgReference("methodTimingPointcut")   // 引用 Pointcut
                .addConstructorArgReference("methodTimingInterceptor") // 引用 Advice
                .getBeanDefinition();
        registry.registerBeanDefinition("methodTimingAdvisor", advisorDef);
        // 这个 Advisor 一旦注册，Spring AOP 就会自动把它织入匹配的 Bean
    }
}
```

### 每一步在做什么？

```
@EnableMethodTiming(basePackages = "com.example.service")
        │
        ▼
Spring 解析到 @Import(MethodTimingRegistrar.class)
        │
        ▼
MethodTimingRegistrar.registerBeanDefinitions()
        │
        ├─ ① 读取 basePackages → "com.example.service"
        ├─ ② 构建表达式 → "execution(* com.example.service..*.*(..))"
        ├─ ③ 注册 MethodTimingInterceptor Bean
        ├─ ④ 注册 AspectJExpressionPointcut Bean（指定哪些方法被拦截）
        └─ ⑤ 注册 DefaultPointcutAdvisor Bean（绑定切入点和拦截器）
                │
                ▼
        Spring AOP 自动织入：com.example.service 包下所有方法
        调用时自动打印耗时
```

---

## 5. Step 4：写 POM

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.5.14</version>
</parent>

<artifactId>method-timing-spring-boot-starter</artifactId>

<dependencies>
    <!-- 提供 @AutoConfiguration、@ConditionalOnXxx 等基础设施 -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-autoconfigure</artifactId>
    </dependency>

    <!-- 提供 MethodInterceptor、Pointcut、Advisor 等 AOP 类 -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-aop</artifactId>
    </dependency>
</dependencies>

<!-- ⚠️ 注意：不要引入 spring-boot-maven-plugin！
     这是 Starter 库，不是可执行 JAR -->
```

---

## 6. 完整调用链路图

```
用户启动 Spring Boot 应用
        │
        ▼
Spring 扫描 @Configuration 类
        │
        ▼
发现 @EnableMethodTiming(basePackages = "com.example.service")
        │
        ▼
@Import 触发 MethodTimingRegistrar.registerBeanDefinitions()
        │
        ├── 注册 "methodTimingInterceptor" → MethodTimingInterceptor
        ├── 注册 "methodTimingPointcut"     → AspectJExpressionPointcut("execution(* com.example.service..*.*(..))")
        └── 注册 "methodTimingAdvisor"      → DefaultPointcutAdvisor(pointcut, interceptor)
                │
                ▼
Spring AOP 在创建 com.example.service 包下的 Bean 时，
检测到 Advisor 匹配，自动创建 AOP 代理
        │
        ▼
用户调用 userService.findById(1L)
        │
        ▼
AOP 代理拦截 → MethodTimingInterceptor.invoke()
        │
        ├─ 记录开始时间: System.currentTimeMillis()
        ├─ invocation.proceed() → 执行真实的 findById()
        └─ 计算耗时并打印: [TIMING] UserService.findById() → 23ms
```

---

## 7. 如何使用（3 步接入）

### 7.1 安装到本地仓库

```bash
cd springboot-starter-learn
mvn clean install -DskipTests
```

### 7.2 在项目中引入依赖

```xml
<dependency>
    <groupId>com.quick</groupId>
    <artifactId>method-timing-spring-boot-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### 7.3 在 @Configuration 上启用

```java
@Configuration
@EnableMethodTiming(basePackages = "com.example.service")
public class AppConfig {
}
```

**完成！** 不需要任何额外配置，`com.example.service` 包下所有方法调用会自动打印耗时。

### 扩展用法

```java
// 监控多个包
@EnableMethodTiming(basePackages = {"com.example.service", "com.example.dao"})

// 不指定包路径 → 自动使用当前类所在的包
@Configuration
@EnableMethodTiming   // 监控 com.example.config 包（当前类所在包）
public class AppConfig {
}
```

---

## 8. 对比：`@EnableXXX` 模式 vs 我之前写的 MyBatis Starter

| 维度 | MyBatis Starter（上一篇文章） | 本 Starter（这篇文章） |
|------|-------------------------------|----------------------|
| **复杂度** | 高（19 个文件，2 个模块） | 低（4 个文件，1 个模块） |
| **触发方式** | `@MyMapperScan` + AutoConfiguration | `@EnableMethodTiming` + `@Import` |
| **是否需要 spring.factories** | 是（AutoConfiguration.imports） | **否**（注解触发，无需 SPI） |
| **核心机制** | FactoryBean + JDK 动态代理 + JDBC | ImportBeanDefinitionRegistrar + AOP |
| **是否有 FactoryBean** | 是（MyMapperFactoryBean） | 否 |
| **Spring 集成方式** | 自动配置（被动发现） | 注解驱动（主动声明） |
| **适用场景** | 完整框架封装 | 轻量功能开关 |

**两种模式各有用武之地：**

- **`@EnableXXX` 模式**：适合**开关型功能**（监控、限流、缓存、定时任务）。用户显式声明才启用。
- **AutoConfiguration 模式**：适合**基础设施型框架**（ORM、RPC、MQ）。引入即用，零声明。

---

## 9. 为什么要这样设计？

### 9.1 `@EnableXXX` 的哲学：显式优于隐式

```java
// 好：一眼就知道开启了方法监控
@EnableMethodTiming(basePackages = "com.example.service")

// 如果是隐式自动配置，用户不知道方法被拦截了
// （什么都没写，但突然开始打印日志 → 惊吓）
```

### 9.2 `ImportBeanDefinitionRegistrar` vs `@Bean` 方法

```java
// 静态方式：包路径写死在 @Bean 方法里
@Bean
public Advisor advisor() {
    return new DefaultPointcutAdvisor(
        new AspectJExpressionPointcut("execution(* com.xxx..*.*(..))"), // 写死了
        new MethodTimingInterceptor()
    );
}

// 动态方式：ImportBeanDefinitionRegistrar 从注解属性读取包路径
// @EnableMethodTiming(basePackages = "com.example.service")  ← 用户决定
//                   ↓
//           Registrar 读取 basePackages，动态构建表达式
```

**`ImportBeanDefinitionRegistrar` 的本质价值：让注解参数决定 Bean 的创建逻辑。**

### 9.3 为什么不放在 AutoConfiguration 里？

因为 `@EnableXXX` 的**包路径参数是运行时动态的**——每个项目不一样。AutoConfiguration 无法预知用户想监控哪个包。这个参数只能由注解传给 `ImportBeanDefinitionRegistrar`。

---

## 10. `ImportBeanDefinitionRegistrar` 的必须要做的事

看完这个例子，你只需要记住 3 个步骤：

```
① 实现 ImportBeanDefinitionRegistrar
       ↓
② 在 registerBeanDefinitions() 中：
   - 从 AnnotationMetadata 读注解参数
   - 用 BeanDefinitionBuilder 构建 BeanDefinition
   - 调用 registry.registerBeanDefinition() 注册
       ↓
③ 在 @EnableXXX 注解上加 @Import(YourRegistrar.class)
```

**就这么简单。** 所有 `@EnableXXX` 系列注解（`@EnableScheduling`、`@EnableAsync`、`@EnableCaching`、`@EnableAspectJAutoProxy`）背后都是这个套路。

---

## 总结

| 要素 | 在这个项目里的体现 |
|------|-------------------|
| `@EnableXXX` 注解 | `@EnableMethodTiming(basePackages = "...")` |
| `@Import` 触发 | `@Import(MethodTimingRegistrar.class)` |
| `ImportBeanDefinitionRegistrar` | `MethodTimingRegistrar`（读取参数 + 注册 Bean） |
| 动态参数传递 | `basePackages` → 注解属性 → AnnotationMetadata → AspectJ 表达式 |
| 最终效果 | 用户指定的包下所有方法自动打印耗时 |

封装一个 `@EnableXXX` 风格的 Starter，你真正要写的核心代码就一个 `ImportBeanDefinitionRegistrar`，剩下的注解只是壳子，拦截器只是具体的业务逻辑。
