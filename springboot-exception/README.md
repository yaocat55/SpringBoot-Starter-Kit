# SpringBoot 全局异常处理模板 —— 复制即用

> **省流版**：把这 4 个文件复制到你项目里，以后写业务只需要 `throw new NotFoundException("用户", 1L)`，前端自动收到统一格式的 JSON。Controller 里永远不用写 try-catch。

---

## 一、这个模块解决什么问题

### 1.1 你现在的代码（反例）

```java
@GetMapping("/{id}")
public Result<User> getById(@PathVariable Long id) {
    try {
        User user = userService.getById(id);
        if (user == null) {
            return Result.fail(404, "用户不存在");  // ← 每个接口都要写一遍
        }
        return Result.ok(user);
    } catch (Exception e) {
        log.error("查询用户失败", e);
        return Result.fail(500, "服务器错误");       // ← 每个接口都要写一遍
    }
}
```

每个 Controller 方法都写一遍 try-catch + if null 判断，十个接口十个样，改一处要改十处。

### 1.2 用了本模块之后（正例）

```java
@GetMapping("/{id}")
public Result<User> getById(@PathVariable Long id) {
    return Result.ok(userService.getById(id));
    // 查不到？Service 里 throw new NotFoundException("用户", id)
    // 参数不对？直接 throw new BadRequestException("ID 必须大于0")
    // Controller 不用写任何判断，全局异常处理器统一兜底
}
```

**所有 Controller 方法都只有一行**：调用 Service + 包装 Result。错误处理全部交给全局异常处理器。

---

## 二、核心设计

```
请求 → Controller（只做参数绑定，不写 try-catch）
           │
           ▼
       Service（遇到错误直接 throw new XxxException(...)）
           │
           ▼
      全局异常处理器 @RestControllerAdvice（统一捕获 → 转成 Result JSON → 返回前端）
```

### 2.1 文件清单（你只需要复制这 4 个到自己的项目）

| 文件 | 作用 | 必须复制？ |
|---|---|---|
| `common/Result.java` | 统一返回体 | **必须** |
| `common/ResultCode.java` | 错误码枚举 | **必须** |
| `exception/BusinessException.java` | 业务异常基类 | **必须** |
| `handler/GlobalExceptionHandler.java` | 全局异常处理器 | **必须** |
| `exception/NotFoundException.java` | 资源不存在异常（示例） | 按需，建议带上 |
| `exception/BadRequestException.java` | 参数错误异常（示例） | 按需，建议带上 |
| `exception/ForbiddenException.java` | 权限不足异常（示例） | 按需 |
| `exception/DuplicateKeyException.java` | 数据冲突异常（示例） | 按需 |
| `exception/ServiceException.java` | 服务端异常（示例） | 按需 |

**核心 4 个文件**总共不到 200 行，复制到项目的 `common` 和 `exception` 包里即可。

---

## 三、三层架构怎么写

### 3.1 Controller 层 —— 只做参数绑定，零 try-catch

```java
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/{id}")
    public Result<User> getById(@PathVariable Long id) {
        return Result.ok(userService.getById(id));
        // 没有 if(user==null)，没有 try-catch
    }

    @PostMapping
    public Result<User> add(@Valid @RequestBody User user) {
        // @Valid 校验失败自动抛 MethodArgumentNotValidException，全局异常处理兜底
        return Result.ok(userService.add(user));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return Result.ok();
    }
}
```

### 3.2 Service 层 —— 遇到错误直接 throw

```java
@Service
public class UserService {

    public User getById(Long id) {
        if (id == null || id <= 0) {
            throw new BadRequestException("用户 ID 必须大于 0");
        }
        User user = db.findById(id);
        if (user == null) {
            throw new NotFoundException("用户", id);  // 直接 throw，不 return null
        }
        return user;
    }

    public void delete(Long id) {
        if (db.remove(id) == null) {
            throw new NotFoundException("用户", id);
        }
    }
}
```

### 3.3 全局异常处理器 —— 统一兜底

```java
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Result<?> handleBusinessException(BusinessException e) {
        log.warn("业务异常 [{}] {}", e.getResultCode().getCode(), e.getMessage());
        return Result.fail(e.getResultCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<?> handleValidation(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return Result.fail(ResultCode.PARAM_VALID_FAILED, detail);
    }

    @ExceptionHandler(Exception.class)
    public Result<?> handleUnknown(Exception e) {
        log.error("未捕获异常", e);
        return Result.fail(ResultCode.INTERNAL_ERROR, "服务器内部错误");
    }
}
```

---

## 四、怎么复制到自己的项目

### Step 1：复制文件

把这 4 个文件复制到你的 SpringBoot 项目：

```
你的项目/
└── src/main/java/com/xxx/yyy/
    ├── common/
    │   ├── Result.java          ← 复制
    │   └── ResultCode.java      ← 复制（改掉包名）
    ├── exception/
    │   ├── BusinessException.java  ← 复制
    │   ├── NotFoundException.java  ← 复制
    │   └── ...（按需复制其他异常）
    └── handler/
        └── GlobalExceptionHandler.java  ← 复制
```

### Step 2：改包名

把 `com.quick.springbootexception` 替换成你的包名。

### Step 3：在 ResultCode 里加你项目的业务错误码

```java
public enum ResultCode {
    // ... 保留通用错误码 ...

    // 你的业务错误码（按模块划分）
    USER_NOT_FOUND(1001, "用户不存在"),
    USER_NAME_DUPLICATE(1002, "用户名已存在"),
    ORDER_NOT_FOUND(2001, "订单不存在"),
    ORDER_STATUS_ERROR(2002, "订单状态不允许此操作"),
    PAY_AMOUNT_MISMATCH(3001, "支付金额与订单金额不一致"),
}
```

### Step 4：要加新的异常？继承 BusinessException 就行

```java
// 一行代码定义一个异常，不需要写别的
public class OrderNotFoundException extends BusinessException {
    public OrderNotFoundException(Long orderId) {
        super(ResultCode.ORDER_NOT_FOUND, "订单ID: " + orderId);
    }
}
```

然后在 Service 里 throw：

```java
public Order getOrder(Long orderId) {
    return orderRepo.findById(orderId)
            .orElseThrow(() -> new OrderNotFoundException(orderId));
}
```

完事了，不用改全局异常处理器，不用改 Controller。

---

## 五、API 测试（curl 直接调）

启动后端口 `8089`。

### 5.1 正常请求

```bash
# 查用户 —— 正常返回
curl http://localhost:8089/api/users/1

# 返回:
# {"code":200,"message":"操作成功","data":{"id":1,"name":"张三",...},"timestamp":"..."}
```

### 5.2 用户不存在 —— 404

```bash
curl http://localhost:8089/api/users/999

# 返回:
# {"code":404,"message":"用户 不存在: 999","data":null,"timestamp":"..."}
```

### 5.3 参数错误 —— 400

```bash
# ID 传了 0
curl http://localhost:8089/api/users/0

# 返回:
# {"code":400,"message":"用户 ID 必须大于 0，收到: 0","data":null,"timestamp":"..."}
```

### 5.4 @Valid 校验失败 —— 422

```bash
curl -X POST http://localhost:8089/api/users \
  -H "Content-Type: application/json" \
  -d '{"name":"","email":"not-an-email","age":-1}'

# 返回:
# {"code":422,"message":"name: 用户名不能为空; email: 邮箱格式不正确; age: 年龄必须大于 0",...}
```

### 5.5 数据重复 —— 409

```bash
curl -X POST http://localhost:8089/api/users \
  -H "Content-Type: application/json" \
  -d '{"name":"张三","email":"test@example.com","age":25}'

# 返回:
# {"code":409,"message":"用户名已存在: 张三",...}
```

### 5.6 服务端内部错误 —— 500

```bash
curl -X POST http://localhost:8089/api/users/error/internal

# 返回:
# {"code":500,"message":"服务器内部错误: ArithmeticException - / by zero",...}
```

### 5.7 接口不存在 —— 404

需要在 yml 加一行才能触发：
```yaml
spring:
  mvc:
    throw-exception-if-no-handler-found: true
  web:
    resources:
      add-mappings: false
```

### 5.8 全部 API 速查

| 方法 | 路径 | 说明 | 触发哪个异常 |
|---|---|---|---|
| GET | `/api/users/1` | 正常查用户 | — |
| GET | `/api/users/999` | 用户不存在 | `NotFoundException(404)` |
| GET | `/api/users/0` | ID 非法 | `BadRequestException(400)` |
| GET | `/api/users/abc/profile` | 参数类型错误 | `MethodArgumentTypeMismatchException` |
| POST | `/api/users` (body 为空) | Body 为空 | `HttpMessageNotReadableException(400)` |
| POST | `/api/users` (合法 JSON) | 新增用户 | — |
| POST | `/api/users` (数据不合法) | @Valid 校验失败 | `MethodArgumentNotValidException(422)` |
| POST | `/api/users` (重名) | 用户名重复 | `DuplicateKeyException(409)` |
| POST | `/api/users/error/internal` | 服务端错误 | `ServiceException(500)` |

---

## 六、统一返回格式

### 6.1 成功

```json
{
  "code": 200,
  "message": "操作成功",
  "data": { "id": 1, "name": "张三", ... },
  "timestamp": "2025-01-15T10:30:00"
}
```

### 6.2 失败

```json
{
  "code": 404,
  "message": "用户 不存在: 999",
  "data": null,
  "timestamp": "2025-01-15T10:30:00"
}
```

`data` 字段加了 `@JsonInclude(NON_NULL)`，为 null 时 JSON 里自动省略 `"data"` 这个 key。

### 6.3 错误码规范

| code 范围 | 含义 | 示例 |
|---|---|---|
| 200 | 成功 | — |
| 400-499 | 客户端错误（参数、权限、资源不存在） | 400 参数错误，404 不存在，422 校验失败 |
| 500-599 | 服务端错误（代码 Bug、第三方挂了） | 500 内部错误，503 不可用 |
| 1000+ | 业务错误（你自己定义的） | 1001 用户不存在，2001 订单不存在 |

---

## 七、全局异常处理器覆盖的异常列表

| 异常 | HTTP code | 说明 |
|---|---|---|
| `BusinessException` | code 从 ResultCode 取 | 你自定义的所有业务异常 |
| `MethodArgumentNotValidException` | 422 | `@Valid` 校验失败 |
| `MissingServletRequestParameterException` | 400 | 缺少 @RequestParam 必填参数 |
| `MethodArgumentTypeMismatchException` | 400 | 参数类型错误（如 ID 传 "abc"） |
| `HttpRequestMethodNotSupportedException` | 405 | GET 接口用了 POST |
| `HttpMessageNotReadableException` | 400 | Body 为空或 JSON 格式错 |
| `NoHandlerFoundException` | 404 | 访问了不存在的 URL |
| `Exception`（兜底） | 500 | 所有未被上面捕获的异常 |

---

## 八、项目结构

```
springboot-exception/
├── pom.xml
├── application.yml
└── src/main/java/com/quick/springbootexception/
    ├── SpringbootExceptionApplication.java
    ├── common/
    │   ├── Result.java              # 统一返回体（static 工厂方法）
    │   └── ResultCode.java          # 错误码枚举（200/4xx/5xx + 业务码）
    ├── exception/
    │   ├── BusinessException.java    # 业务异常基类（带 ResultCode）
    │   ├── NotFoundException.java    # 资源不存在
    │   ├── BadRequestException.java  # 参数错误
    │   ├── ForbiddenException.java   # 权限不足
    │   ├── DuplicateKeyException.java  # 数据冲突
    │   └── ServiceException.java     # 服务端错误
    ├── handler/
    │   └── GlobalExceptionHandler.java  # @RestControllerAdvice 全局兜底
    ├── model/
    │   └── User.java                # 实体（带 @Valid 校验注解）
    ├── service/
    │   └── UserService.java         # 演示 Service 层如何 throw 异常
    └── controller/
        └── UserController.java      # 演示 Controller 层零 try-catch
```
