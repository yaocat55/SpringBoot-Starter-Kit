# springboot-lombok

SpringBoot Lombok 常用注解示例，面向初学者的开箱即用演示。

## 注解速查

| 注解 | 作用 | 示例类 |
|------|------|--------|
| `@Data` | 等价于 @Getter + @Setter + @ToString + @EqualsAndHashCode + @RequiredArgsConstructor | `User.java` |
| `@Builder` | 生成 Builder 模式构造器 | `User.java`, `ImmutableConfig.java`, `Product.java` |
| `@Builder.Default` | 指定 Builder 模式下字段的默认值（不用此注解默认值会被忽略） | `User.java`, `ImmutableConfig.java` |
| `@NoArgsConstructor` | 生成无参构造器 | `User.java` |
| `@AllArgsConstructor` | 生成全参构造器 | `User.java`, `Product.java` |
| `@Accessors(chain = true)` | setter 返回 this，支持链式调用 | `User.java` |
| `@Value` | 不可变类：final 字段 + @Getter + @EqualsAndHashCode + @ToString + 全参构造（**没有** setter） | `ImmutableConfig.java` |
| `@NonNull` | 在 setter/构造器中自动生成 null 检查 | `User.java` |
| `@ToString.Exclude` | 排除某个字段不参与 toString() | `User.java`, `OrderDTO.java` |
| `@ToString.Include` | 显式标记字段参与 toString()，可自定义别名 | `OrderDTO.java` |
| `@EqualsAndHashCode.Include` | 显式指定哪些字段参与 equals/hashCode | `OrderDTO.java` |
| `@Getter(AccessLevel.NONE)` | 不为该字段生成 getter | `User.java` |
| `@With` | 生成 withXxx() 方法返回修改了该字段的新对象副本 | `Product.java` |
| `@Slf4j` | 自动生成 log 字段（使用 SLF4J） | `UserService.java`, `UserController.java`, 主类 |
| `@RequiredArgsConstructor` | 为 final 字段生成构造器，Spring 会通过它自动注入 | `UserService.java`, `UserController.java` |
| `@SneakyThrows` | 悄悄抛出受检异常，无需在方法签名声明 throws | `UserService.java`, `CleanupExample.java` |
| `@Cleanup` | 自动关闭资源（等价 try-with-resources，更简洁） | `CleanupExample.java` |

## 项目结构

```
src/main/java/com/quick/springbootlombok/
├── SpringbootLombokApplication.java   # 启动类 + CommandLineRunner 演示
├── controller/
│   └── UserController.java            # @Slf4j + @RequiredArgsConstructor
├── model/
│   ├── User.java                      # @Data @Builder @Accessors @NonNull ...
│   ├── ImmutableConfig.java           # @Value 不可变类
│   ├── OrderDTO.java                  # @Getter @Setter @ToString @EqualsAndHashCode
│   └── Product.java                   # @With 不可变风格更新
├── service/
│   └── UserService.java               # @Slf4j @RequiredArgsConstructor @SneakyThrows
└── util/
    └── CleanupExample.java            # @Cleanup @SneakyThrows
```

## 关键要点

### @Builder 和 @Builder.Default

```java
@Builder
public class User {
    private String username;          // builder 默认 null
    @Builder.Default
    private Boolean active = true;    // builder 默认 true
}
```

**如果不加 `@Builder.Default`，`= true` 的默认值在 Builder 模式下会被忽略（变成 null/false/0）。**

### @Data vs @Value

- `@Data` —— 可变对象，有 getter + setter
- `@Value` —— **不可变**对象，只有 getter，没有 setter，类也是 final 的

### @RequiredArgsConstructor 注入

```java
@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository repo;  // Spring 通过构造器自动注入
}
```

比 `@Autowired` 更推荐：无需反射、可测试性好、字段可为 final。

## Lombok 原理

### 核心：编译期修改 AST

Lombok 通过 **JSR 269（注解处理器 API）** 在编译期直接操作 javac 的抽象语法树（AST），往里面塞代码。

```
源码                    javac 解析          Lombok 插手修改 AST       javac 继续
.java  ──→  生成 AST   ──→  注解处理器  ──→  AST 多了新节点    ──→  .class
```

### 三步走

**1. 注册注解处理器**

`lombok.jar` 里的 `META-INF/services/javax.annotation.processing.Processor` 文件声明了 `LombokProcessor`，javac 启动时会自动发现并加载它。

**2. 拦截 AST**

javac 把源码解析成 AST 后，在生成字节码之前，调用注解处理器。Lombok 拿到这棵完整的 AST 树。

**3. 修改 AST 节点**

遍历 AST，找到带 Lombok 注解的类/字段/方法节点，直接往树上追加新节点。比如 `@Data` 就让类节点多出 getter、setter、toString、equals、hashCode 方法。

### 为什么 IDE 要装插件

IDE 的增量编译器通常不走 javac 的注解处理流程，所以需要装 Lombok 插件，用同样的逻辑去改 IDE 内部的 AST。否则 IDE 看到的源码就没有那些生成的方法，全部飘红。

### 运行时不需要 Lombok

注解处理器只在编译期干活。编译出来的 `.class` 文件里 getter/setter 已经是实实在在的方法了，所以运行时不需要 Lombok。这也是 Maven 里 Lombok 要标 `<optional>true</optional>`，并且 `spring-boot-maven-plugin` 打包时要 exclude 它的原因。

### @Accessors(chain = true)

```java
// 普通 setter: user.setName("x"); user.setAge(18);
// 链式 setter:
user.setName("x").setAge(18);
```
