# Spring Boot 集成七牛云 —— 音视频上传全流程方案

基于七牛云服务端下发上传凭证 + 前端直传 + 后处理（转码/截图/回调）的完整示例。

## 架构流程

```
┌──────────┐      ┌──────────────────┐      ┌───────────┐
│  前端     │      │      后端         │      │  七牛云    │
└────┬─────┘      └───────┬──────────┘      └─────┬─────┘
     │                    │                       │
     │ ① GET /upload-token│                       │
     │───────────────────→│                       │
     │    token + domain   │                       │
     │←───────────────────│                       │
     │                    │                       │
     │ ② POST (file+token)│                       │
     │───────────────────────────────────────────→│
     │         key + 元信息                        │
     │←───────────────────────────────────────────│
     │                    │                       │
     │ ③ POST /upload-callback?key=xxx            │
     │───────────────────→│                       │
     │                    │ ④ 触发转码 pfop        │
     │                    │──────────────────────→│
     │                    │    persistentId        │
     │                    │←──────────────────────│
     │                    │                       │
     │                    │ ⑤ 转码完成回调          │
     │                    │←──────────────────────│
     │  ⑥ GET /private-url│                       │
     │───────────────────→│                       │
     │    签名播放地址      │                       │
     │←───────────────────│                       │
```

## 核心思路

| 步骤 | 说明 |
|------|------|
| ① 获取凭证 | 后端通过 AK/SK + Bucket 生成上传 token，支持 returnBody / callbackUrl / persistentOps 等策略 |
| ② 前端直传 | 前端拿到 token 后通过 `FormData` 直传七牛云，文件不经过后端，节省带宽 |
| ③ 保存记录 | 前端拿到七牛返回的 key 后提交给后端，或七牛服务端直接回调后端 |
| ④ 触发转码 | 上传完成时自动触发或后端手动调用 pfop 进行 HLS/mp4/截图等处理 |
| ⑤ 转码回调 | 转码完成后七牛回调后端通知处理结果 |
| ⑥ 签名播放 | 私有空间的文件通过后端签名生成带时效的播放 URL |

## 快速开始

### 1. 七牛云控制台准备

前往 [https://portal.qiniu.com](https://portal.qiniu.com)：

- **密钥管理** → 获取 AccessKey / SecretKey
- **对象存储** → 新建存储空间 Bucket（建议私有空间，防盗链）
- **CDN** → 绑定加速域名（或使用默认测试域名）
- **多媒体处理** → 创建持久化处理队列（pipeline），用于音视频转码

### 2. 配置文件

```yaml
# application.yml
qiniu:
  access-key: your-access-key
  secret-key: your-secret-key
  bucket: your-bucket
  domain: https://cdn.example.com          # CDN 域名
  expire-seconds: 3600                     # 上传凭证有效期
  callback-url: https://api.yours.com/qiniu/qiniu-callback     # 上传回调（需公网可达）
  persistent-pipeline: your-pipeline-name   # 转码队列（在七牛控制台创建）
  persistent-notify-url: https://api.yours.com/qiniu/pfop-callback  # 转码完成回调
```

### 3. Maven 依赖

```xml
<dependency>
    <groupId>com.qiniu</groupId>
    <artifactId>qiniu-java-sdk</artifactId>
    <version>7.17.0</version>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
```

## API 接口一览

### 上传凭证

| 接口 | 方法 | 场景 |
|------|------|------|
| `/qiniu/upload-token` | GET | 普通文件（图片/文档），带 returnBody 返回元信息 |
| `/qiniu/upload-token/callback?userId=&bizType=` | GET | 带七牛服务端回调的上传凭证，防止前端关闭丢失记录 |
| `/qiniu/upload-token/resumable?key=&mimeLimit=` | GET | 视频大文件断点续传，限制 mime 类型为 `video/*;audio/*` |
| `/qiniu/upload-token/with-transcoding?fops=&outputKeyPrefix=` | GET | 上传时自动触发转码（persistentOps） |

### 上传结果回调

| 接口 | 方法 | 场景 |
|------|------|------|
| `/qiniu/upload-callback?key=&fileName=&fsize=&mimeType=` | POST | 前端手动提交上传结果 |
| `/qiniu/qiniu-callback` | POST | 七牛服务端回调端点（需公网可达） |

### 转码（持久化处理）

| 接口 | 方法 | 场景 |
|------|------|------|
| `/qiniu/pfop` | POST | 对已有文件触发转码 |
| `/qiniu/pfop/hls?key=&outputPrefix=` | POST | 一键 HLS 多码率转码（360p/720p/1080p） |
| `/qiniu/pfop/snapshot?key=&outputKey=` | POST | 截取视频封面 |
| `/qiniu/pfop-callback` | POST | 转码完成回调端点 |

### 媒体信息

| 接口 | 方法 | 场景 |
|------|------|------|
| `/qiniu/stat?key=` | GET | 文件基本信息（大小/hash/MIME） |
| `/qiniu/avinfo?key=` | GET | 音视频元信息（时长/分辨率/码率/编码/流信息） |

### 文件访问

| 接口 | 方法 | 场景 |
|------|------|------|
| `/qiniu/private-url?key=&expireIn=3600` | GET | 私有空间签名下载/播放链接 |
| `/qiniu/delete?key=` | DELETE | 删除文件 |

## 关键代码说明

### 上传凭证策略（QiniuService）

```java
// ① returnBody：前端直接获取文件元信息
StringMap policy = new StringMap();
policy.put("returnBody",
    "{\"key\":\"$(key)\",\"hash\":\"$(etag)\",\"fsize\":$(fsize),\"mimeType\":\"$(mimeType)\"}");
String token = auth.uploadToken(bucket, null, expireSeconds, policy);

// ② callbackUrl：七牛服务端回调（保证上传记录不丢失）
policy.put("callbackUrl", "https://api.yours.com/qiniu/qiniu-callback");
policy.put("callbackBody", "key=$(key)&hash=$(etag)&fsize=$(fsize)&mimeType=$(mimeType)");

// ③ persistentOps：上传时自动触发转码
String entry = UrlSafeBase64.encodeToString(bucket + ":" + outputKey);
policy.put("persistentOps", "avthumb/mp4/s/1280x720/vb/2000k|saveas/" + entry);
policy.put("persistentPipeline", "your-pipeline");
policy.put("persistentNotifyUrl", "https://api.yours.com/qiniu/pfop-callback");
```

### 持久化处理 fops 指令参考

```bash
# 转 MP4 720p
avthumb/mp4/s/1280x720/vb/2000k

# 转 HLS (m3u8) 自适应码率
avthumb/m3u8/s/640x360/vb/600k

# 视频截图（第 1 秒，480x360）
vframe/jpg/offset/1/w/480/h/360

# 多规格一键提交（分号分隔）
avthumb/mp4/s/1280x720/vb/2000k|saveas/<base64UrlSafe(bucket:key_720p.mp4)>;\
avthumb/m3u8/s/640x360/vb/600k|saveas/<base64UrlSafe(bucket:key_360p.m3u8)>
```

### 私有空间签名 URL

```java
// 公开空间：直接拼接
String url = domain + "/" + key;

// 私有空间：签名后带 token 参数，可设置过期时间
String signedUrl = auth.privateDownloadUrl(publicUrl, 3600);
// → https://cdn.example.com/file.mp4?e=1716000000&token=xxxxx
```

## 前端上传示例（视频场景）

```javascript
async function uploadVideo(file, onProgress) {
  // 1. 获取断点续传凭证
  const tokenRes = await fetch(
    `/qiniu/upload-token/resumable?key=${encodeURIComponent(file.name)}&mimeLimit=video/*`
  );
  const { data: { token, domain, uploadUrl } } = await tokenRes.json();

  // 2. 使用 qiniu-js-sdk 分片上传（支持进度回调、断点续传）
  const observable = qiniu.upload(file, file.name, token, {
    fname: file.name,
    mimeType: file.type,
  }, {
    useCdnDomain: true,
    region: qiniu.region.z0,      // 根据 bucket 区域选择
  });

  return new Promise((resolve, reject) => {
    observable.subscribe({
      next: (res) => {
        // 上传进度
        onProgress && onProgress(res.total.percent);
      },
      error: (err) => reject(err),
      complete: (res) => {
        // 3. 拿到 key 后提交后端保存
        const { key, hash } = res;
        fetch(`/qiniu/upload-callback?key=${encodeURIComponent(key)}&fsize=${file.size}&mimeType=${file.type}`, {
          method: 'POST'
        });

        // 4. 触发 HLS 转码
        fetch(`/qiniu/pfop/hls?key=${encodeURIComponent(key)}&outputPrefix=videos/hls/${key}`, {
          method: 'POST'
        });

        resolve({ key, url: `${domain}/${key}` });
      }
    });
  });
}
```

## 音视频场景最佳实践

### 流程建议

1. **上传阶段**：使用 `/upload-token/resumable` 获取断点续传 token，限制 `mimeLimit=video/*`
2. **回调保障**：使用 `/upload-token/callback` 让七牛服务端回调后端，避免前端关闭丢失记录
3. **元信息**：上传完成后调 `/avinfo` 获取时长/分辨率/码率等，存入数据库
4. **转码**：调 `/pfop/hls` 一键生成 360p/720p/1080p 三个清晰度
5. **封面**：调 `/pfop/snapshot` 截取视频封面
6. **播放**：私有空间通过 `/private-url` 签名后返回前端播放

### 数据库设计参考

```sql
CREATE TABLE media_file (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    bucket      VARCHAR(64),           -- 存储空间
    file_key    VARCHAR(256) NOT NULL, -- 七牛云文件 key
    file_name   VARCHAR(256),          -- 原始文件名
    file_size   BIGINT,                -- 文件大小（字节）
    mime_type   VARCHAR(64),           -- MIME 类型
    hash        VARCHAR(64),           -- 文件 hash
    duration    DOUBLE,                -- 视频时长（秒）
    width       INT,                   -- 视频宽度
    height      INT,                   -- 视频高度
    bitrate     INT,                   -- 码率（kbps）
    url         VARCHAR(512),          -- 拼接的访问 URL
    status      VARCHAR(32),           -- UPLOADED / TRANSCODING / COMPLETED / FAILED
    avinfo      JSON,                  -- 原始 avinfo 数据
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE transcode_task (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    media_file_id   BIGINT,
    persistent_id   VARCHAR(128),      -- pfop 返回的 persistentId
    source_key      VARCHAR(256),
    output_key      VARCHAR(256),
    spec            VARCHAR(32),       -- 360p / 720p / 1080p
    format          VARCHAR(16),       -- mp4 / m3u8 / jpg
    status          VARCHAR(32),       -- PROCESSING / COMPLETED / FAILED
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP
);
```

## 潜在坑点

- **Region 自动选择**：`Region.autoRegion()` 适合多数场景，网络受限时可手动指定 `Region.region0()` 等
- **Token 过期**：默认 3600 秒，前端需在此时间内完成上传；分片上传场景建议适当延长
- **Key 覆盖**：同名 key 会覆盖旧文件，生产环境用 UUID 或 `${userId}/${timestamp}/${fileName}` 作为 key
- **回调鉴权**：七牛服务端回调支持设置 `callbackAuth`，生产环境务必开启防止伪造回调
- **持久化处理队列**：转码需要预先在七牛控制台创建 pipeline，否则 pfop 会失败
- **私有空间**：直接拼接的 URL 无法访问，必须通过 `auth.privateDownloadUrl()` 签名
- **HLS 跨域**：如使用 HLS 播放，需在七牛控制台配置 CORS 允许跨域访问 m3u8/ts 文件
- **转码费用**：音视频转码按时长计费，建议限制用户上传的视频大小和时长
