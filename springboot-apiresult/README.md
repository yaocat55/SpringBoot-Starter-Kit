# SpringBoot API 统一返回模板

> 把项目中 90% 的 CRUD 接口标准化——前端只需要记一套参数规则和一套返回格式，所有接口通用。内置 20 条数据，启动即可联调。

---

## 一、前端联调速查

### 1.1 统一返回格式

无论成功失败，后端返回的 JSON **结构完全一样**：

```json
{
  "code": 200,
  "msg": "success",
  "data": { ... },
  "timestamp": 1779600000000
}
```

- **`code`**：200=成功，400=参数错，1001起=业务错误，500=系统异常
- **`msg`**：信息，成功时是 `"success"`，失败时是具体原因
- **`data`**：实际数据（分页查是分页结构，单条查是对象，增删改是对象或 null）

**错误时**（code ≠ 200）前端直接取 `msg` 弹提示即可：

```json
{ "code": 1001, "msg": "数据不存在: id=999" }
{ "code": 1002, "msg": "数据重复: 邮箱已存在: test@example.com" }
{ "code": 400,  "msg": "参数错误: 不支持的排序字段: password" }
{ "code": 400,  "msg": "name: 用户名不能为空; email: 邮箱不能为空; age: 年龄不能为空" }
```

### 1.2 用户数据模型

```json
{
  "id": 1,
  "name": "张三",
  "email": "zhangsan@example.com",
  "age": 25,
  "status": "active",
  "role": "admin"
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | Long | 主键 |
| `name` | String | 用户名（2-20字符，必填） |
| `email` | String | 邮箱（必填，唯一） |
| `age` | Integer | 年龄（1-150，必填） |
| `status` | String | `active` / `inactive`（默认 active） |
| `role` | String | `admin` / `editor` / `viewer`（默认 viewer） |

### 1.3 分页返回结构

```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "pageNo": 1,
    "pageSize": 10,
    "totalPage": 2,
    "totalCount": 20,
    "data": [
      { "id": 1, "name": "张三", ... },
      { "id": 2, "name": "张三丰", ... }
    ]
  }
}
```

| 字段 | 说明 |
|---|---|
| `pageNo` | 当前第几页 |
| `pageSize` | 每页多少条 |
| `totalPage` | 总共多少页（后端自动算） |
| `totalCount` | 总共多少条记录 |
| `data` | **当前页的数据数组**（注意嵌套在 data 里） |

---

## 二、前端调什么参数

### 2.1 分页参数（所有 GET 列表接口通用）

```
?pageNo=1       第几页，不传默认 1
&pageSize=10    每页条数，不传默认 10，最大 100
&sortField=age,desc      按 age 降序
&sortField=age,desc&sortField=name,asc   多列排序：先 age 降序，再 name 升序
```

### 2.2 模糊搜索

```
?blurry=张      在 name 和 email 里模糊匹配
```

### 2.3 条件筛选参数（`/condition` 接口）

这些都是**可选参数**，传了哪个就按哪个筛选，不传不筛：

```
?status=active          精确匹配状态
&role=admin             精确匹配角色
&email=zhangsan@example.com   精确匹配邮箱
&idList=1&idList=2&idList=3   IN 查询
&betweenTime=2025-01-01,2025-12-31   时间范围
&createBeginTime=2025-01-01          时间起
&createEndTime=2025-06-30            时间止
```

参数可以**任意组合**，比如同时搜状态+角色+关键词：

```
?status=active&role=editor&blurry=li&pageNo=1&pageSize=10
```

---

## 三、所有接口一览

### 3.1 分页列表

```bash
# 全量，第1页，每页10条（默认）
GET /api/users

# 第2页，每页5条
GET /api/users?pageNo=2&pageSize=5

# 模糊搜索 + 排序
GET /api/users?blurry=zhang&sortField=age,desc&pageSize=5
```

响应：分页结构（见 1.3）。

### 3.2 条件搜索

```bash
# 查所有 admin
GET /api/users/condition?status=admin

# 查 active 的 editor
GET /api/users/condition?status=active&role=editor

# 按 ID 列表查
GET /api/users/condition?idList=1&idList=2&idList=3

# 组合：模糊 + 状态 + 时间范围 + 排序
GET /api/users/condition?blurry=li&status=active&betweenTime=2025-01-01,2025-12-31&sortField=age,desc
```

响应同样是分页结构。

### 3.3 查单个

```bash
GET /api/users/1
```

成功响应：
```json
{
  "code": 200,
  "msg": "success",
  "data": { "id": 1, "name": "张三", "email": "zhangsan@example.com", "age": 25, "status": "active", "role": "admin" }
}
```

不存在：
```json
{ "code": 1001, "msg": "用户不存在: id=999" }
```

### 3.4 新增

```bash
POST /api/users
Content-Type: application/json

{ "name": "新用户", "email": "new@test.com", "age": 25 }
```

成功返回该对象（id 自动回填）。如邮箱已存在返回：

```json
{ "code": 1002, "msg": "邮箱已存在: new@test.com" }
```

返回体校验失败：

```json
{ "code": 400, "msg": "name: 用户名不能为空; email: 邮箱不能为空; age: 年龄不能为空" }
```

### 3.5 更新

```bash
PUT /api/users/1
Content-Type: application/json

{ "name": "新名字" }
```

**只传你要改的字段**，不传的字段不动。

### 3.6 删除

```bash
DELETE /api/users/1
```

成功返回：
```json
{ "code": 200, "msg": "success", "data": null }
```

---

## 四、前端统一处理方案（axios 示例）

```js
// 请求拦截器
axios.interceptors.response.use(
  res => {
    const body = res.data
    if (body.code !== 200) {
      // 业务错误
      ElMessage.error(body.msg)
      return Promise.reject(new Error(body.msg))
    }
    return body.data  // 直接脱壳，代码里只拿 data
  },
  err => {
    ElMessage.error('网络异常')
    return Promise.reject(err)
  }
)
```

---

## 五、后端设计（面向接手后端的新人）

### 5.1 核心思路

所有 CRUD 接口走同一套流程：

```
Controller 接收参数
  → Service 调用 Mapper
    → Mapper XML 执行 SQL
  → Service 组装 PageResult
→ Controller 包 ApiResult 返回
```

异常由 `GlobalExceptionHandler` 统一兜底；非 `ApiResult` 的返回值由 `ResponseAdvice` 自动包装。

### 5.2 请求参数继承体系

```
PageRequest           分页参数（pageNo / pageSize / sortField）
  └─ ConditionRequest  +模糊+日期范围（blurry / betweenTime / createBeginTime / createEndTime）
       └─ UserQuery    +业务筛选字段（status / role / email / idList）
```

Controller 只写一个参数 `UserQuery`，分页+模糊+筛选全在里面，前端传了什么 Spring 自动映射什么。

### 5.3 Mapper 继承体系

```
BaseMapper<K, V>           公共接口（searchCount / searchByCondition）
  └─ UserMapper            继承 BaseMapper<User, UserQuery>
```

`UserMapper.xml` 里所有的 `queryWhere` 片段和 `BaseMapper.xml` 的 `paginationSql` 片段组合成完整 SQL，新增用 `trim` 做动态 INSERT，更新同样用 `trim` 只 SET 非空字段。

### 5.4 写一个新 CRUD 只需 4 步

```
建表 → 创建 XxxQuery 继承 ConditionRequest（加业务筛选字段）
     → 创建 XxxMapper 继承 BaseMapper<Xxx, XxxQuery> + XxxMapper.xml（写 queryWhere）
     → 写 Service + Controller
```

不用 try-catch，不用手写 LIMIT，不用手拼 WHERE。

### 5.5 注解驱动异常（新增异常类型只需 13 行）

不用在每个 catch 里写 `ApiResult.error(code, msg)`，也不用在 Service 里传错误码数字。定义异常子类 + 贴 `@BizErrorCode` 注解即可：

```java
// 1. 定义异常类（13 行，一次写好永久复用）
@BizErrorCode(code = 1001, message = "数据不存在")   // ← 注解声明 code + msg 前缀
public class NotFoundException extends BizException {
    public NotFoundException(String detail) { super(detail); }
}

// 2. Service 里直接扔（不传 code，注解自动提供）
throw new NotFoundException("id=" + id);
// → {"code":1001, "msg":"数据不存在: id=1"}

throw new DuplicateException("邮箱已存在: test@example.com");
// → {"code":1002, "msg":"数据重复: 邮箱已存在: test@example.com"}
```

**原理**：`GlobalExceptionHandler` 读到异常类上的 `@BizErrorCode` 注解，自动取 `code()` 拼 `message() + ": " + 异常 detail`。

**已有子类**（在 `exception/` 下，拿来直接用）：

| 类 | @BizErrorCode | 用途 |
|---|---|---|
| `NotFoundException` | `code=1001, message=数据不存在` | 查单条/更新/删除时数据不存在 |
| `DuplicateException` | `code=1002, message=数据重复` | 唯一键冲突 |
| `BadRequestException` | `code=400, message=参数错误` | 参数校验不通过 |

**扩展**：你项目里加新异常类型，只需建子类 + 贴 `@BizErrorCode`，不用改 Handler。**

### 5.6 错误码说明

| code | 说明 |
|---|---|
| 200 | 成功 |
| 400 | 参数错误（前端改参数即可） |
| 1001 | 数据不存在 |
| 1002 | 数据重复 |
| 500 | 系统异常（需要后端排查） |
| 自定义 | 在 `ErrorCode.java` 里加 |

---

## 六、复制到新项目

1. 把整个模块的 `common/`、`exception/`、`handler/`、`advice/`、`mapper/`、`model/`、`resources/mapper/` 复制过去
2. 全局替换 `com.quick.springbootapiresult` → 你的包名（含 `BaseMapper.xml` 的 namespace 引用）
3. 在 `ErrorCode.java` 里加你的业务错误码
4. pom.xml 加 MyBatis + 数据库驱动，application.yml 改数据库连接
5. 删掉 `UserMapper` / `UserService` / `UserController` / `UserQuery` / `User`，换成你自己的

---

## 七、项目结构

```
springboot-apiresult/
├── pom.xml
├── application.yml
└── src/main/
    ├── java/com/quick/springbootapiresult/
    │   ├── common/
    │   │   ├── ApiResult.java          # 统一返回体
    │   │   ├── ErrorCode.java          # 错误码
    │   │   ├── BaseMapper.java         # 公共 Mapper 接口
    │   │   ├── PageRequest.java        # 分页请求
    │   │   ├── PageResult.java         # 分页结果
    │   │   └── ConditionRequest.java   # 条件查询基类
    │   ├── exception/
    │   │   ├── BizErrorCode.java         ← 注解
    │   │   ├── BizException.java
    │   │   ├── NotFoundException.java     ← 预置子类
    │   │   ├── DuplicateException.java
    │   │   └── BadRequestException.java
    │   ├── handler/GlobalExceptionHandler.java
    │   ├── advice/ResponseAdvice.java
    │   ├── mapper/UserMapper.java
    │   ├── model/
    │   │   ├── User.java
    │   │   └── UserQuery.java
    │   ├── service/UserService.java
    │   └── controller/UserController.java
    └── resources/
        ├── sql/
        │   ├── schema.sql
        │   └── data.sql
        └── mapper/
            ├── BaseMapper.xml
            └── UserMapper.xml
```
