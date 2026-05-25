# SpringBoot + Nacos 配置中心 & 服务发现

> 启动一个 Nacos → 启动本应用 → 配置中心动态刷新 + 服务发现注册发现，全程可验证。

---

## 一、你需要什么

| 东西 | 用途 |
|---|---|
| Docker | 启动 Nacos 服务端（一条命令） |
| Nacos 控制台 | 浏览器访问 `http://localhost:8848/nacos` 管理配置 |

没装 Docker？去 [docker.com](https://www.docker.com/products/docker-desktop/) 下载安装。

---

## 二、启动 Nacos（30 秒搞定）

```bash
# 1. 进入模块目录
cd springboot-nacos

# 2. 启动 Nacos
docker-compose up -d

# 3. 等 10 秒，打开控制台
# http://localhost:8848/nacos
# 登录账号：nacos
# 登录密码：nacos
```

看到 Nacos 控制台登录页就说明 Nacos 跑起来了。

---

## 三、在 Nacos 控制台创建配置

应用启动后会从 Nacos 读取 `nacos-demo.yaml` 这个配置文件，所以先在控制台建好它：

**操作步骤**：

1. 登录后点左侧菜单 **配置管理 → 配置列表**
2. 点右上角 **＋** 号（新建配置）
3. 填以下内容：

```
Data ID:    nacos-demo.yaml
Group:      DEFAULT_GROUP
配置格式:    YAML
配置内容:
  app:
    title: 订单管理系统
    version: 2.0.0
    author: 张三
```

4. 点 **发布**

![在哪填](就是左侧菜单 配置管理→配置列表→点+号)

---

## 四、启动应用

```bash
# 在 springboot-nacos 目录下
mvn spring-boot:run
```

启动日志里出现这两行就对了：

```
[Nacos Config] loaded config data ... nacos-demo.yaml
[Nacos Discovery] register service ... nacos-demo 192.168.x.x:8080
```

没出现？检查 Nacos 是不是还在跑：`docker ps | grep nacos`

---

## 五、验证配置中心

### 5.1 读配置

```bash
curl http://localhost:8080/api/config
# → {"title":"订单管理系统","version":"2.0.0","author":"张三"}
```

这三个值都是从 Nacos 配置中心实时读到的。

### 5.2 动态刷新（不用重启应用）

1. 回到 Nacos 控制台 → 配置列表 → 点 `nacos-demo.yaml` 右边的 **编辑**
2. 把 `title` 改成 `"新标题-已刷新"`
3. 点 **发布**
4. 再次请求：

```bash
curl http://localhost:8080/api/config/title
# → "新标题-已刷新"
```

**没重启应用，值就变了** —— 这就是 `@RefreshScope` 的作用。

---

## 六、验证服务发现

### 6.1 看本服务有没有注册上去

```bash
curl http://localhost:8080/api/discovery/self | python -m json.tool
```

返回本服务在 Nacos 上的注册信息（IP、端口、健康状态）。同时去 Nacos 控制台 **服务管理 → 服务列表**，能看到 `nacos-demo`。

### 6.2 看 Nacos 上所有服务

```bash
curl http://localhost:8080/api/discovery/services
# → ["nacos-demo"]
```

### 6.3 通过服务名调用（不写 IP）

```bash
# 用"服务名"代替"IP:端口"，Nacos 自动解析
curl http://localhost:8080/api/discovery/call/nacos-demo/api/config/title
# → "新标题-已刷新"
```

这里 `nacos-demo` 是服务名，不是 IP。Nacos 自动把它换成实际的 `192.168.x.x:8080`。

---

## 七、核心代码看一眼

### 7.1 配置中心怎么读

```java
@RefreshScope   // ← 让下面的 @Value 自动刷新
@RestController
public class ConfigController {

    @Value("${app.title:默认标题}")   // ← 冒号后面是默认值（Nacos 没有时用这个）
    private String title;
}
```

### 7.2 服务发现怎么调

```java
// Step 1：声明一个支持负载均衡的 RestTemplate
@Bean
@LoadBalanced          // ← 让 "服务名" 变成 "IP:端口"
public RestTemplate restTemplate() {
    return new RestTemplate();
}

// Step 2：直接写服务名
String url = "http://nacos-demo/api/config";  // ← nacos-demo 不是域名，是服务名
restTemplate.getForObject(url, Object.class);
```

### 7.3 配置文件怎么说

- **`bootstrap.yml`**：写 Nacos 配置中心的地址、Data ID 规则（优先级高，最早加载）
- **`application.yml`**：写 Nacos 服务发现的地址、本应用端口

---

## 八、全部接口

| 接口 | 说明 |
|---|---|
| `GET /api/config` | 读 Nacos 上的全部配置 |
| `GET /api/config/title` | 读单个配置项 |
| `GET /api/discovery/self` | 查看本服务在 Nacos 上的注册信息 |
| `GET /api/discovery/services` | 列出 Nacos 上所有注册的服务名 |
| `GET /api/discovery/services/detail` | 列出所有服务 + 其实例地址 |
| `GET /api/discovery/call/{服务名}/**` | 通过服务名调用另一个服务 |
| `GET /api/discovery/hello` | 健康检查 |

---

## 九、关掉

```bash
# 停应用（Ctrl+C）

# 停 Nacos
docker-compose down
```

---

## 十、拿回自己项目怎么改

1. 复制整个 `springboot-nacos` 目录
2. `bootstrap.yml` 里把 `spring.application.name` 改成你的服务名
3. `application.yml` 里把端口改成你的
4. `ConfigController` 里把 `@Value` 改成你需要的配置项
5. 你的其他服务调用本服务时，`RestTemplate` 的 URL 里写 `http://你的服务名/接口路径`

Nacos 地址 `127.0.0.1:8848` 生产环境换成你的 Nacos 集群地址。
