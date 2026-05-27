package com.quick.springbootqiniu.service;

import com.qiniu.common.QiniuException;
import com.qiniu.processing.OperationManager;
import com.qiniu.storage.BucketManager;
import com.qiniu.storage.UploadManager;
import com.qiniu.storage.model.FileInfo;
import com.qiniu.util.Auth;
import com.qiniu.util.StringMap;
import com.qiniu.util.UrlSafeBase64;
import com.quick.springbootqiniu.config.QiniuConfig;
import com.quick.springbootqiniu.model.UploadTokenResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Service
public class QiniuService {

    private static final Logger log = LoggerFactory.getLogger(QiniuService.class);
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final Auth auth;
    private final QiniuConfig config;
    private final BucketManager bucketManager;
    private final OperationManager operationManager;

    public QiniuService(Auth auth, QiniuConfig config,
                        UploadManager uploadManager,
                        BucketManager bucketManager,
                        OperationManager operationManager) {
        this.auth = auth;
        this.config = config;
        this.bucketManager = bucketManager;
        this.operationManager = operationManager;
    }

    // ======================== 1. 简单上传凭证 ========================

    public UploadTokenResult simpleUploadToken() {
        StringMap policy = new StringMap();
        policy.put("returnBody",
                "{\"key\":\"$(key)\"," +
                "\"hash\":\"$(etag)\"," +
                "\"fsize\":$(fsize)," +
                "\"mimeType\":\"$(mimeType)\"," +
                "\"bucket\":\"$(bucket)\"," +
                "\"ext\":\"$(ext)\"," +
                "\"imageInfo\":$(imageInfo)," +
                "\"avinfo\":$(avinfo)}");

        String token = auth.uploadToken(config.getBucket(), null,
                config.getExpireSeconds(), policy);
        return UploadTokenResult.of(token, config.getDomain());
    }

    // ======================== 2. 带服务端回调的上传凭证 ========================

    public UploadTokenResult uploadTokenWithCallback(Map<String, String> customData) {
        StringMap policy = new StringMap();

        if (config.getCallbackUrl() != null && !config.getCallbackUrl().isBlank()) {
            policy.put("callbackUrl", config.getCallbackUrl());
            String cbBody = "key=$(key)" +
                    "&hash=$(etag)" +
                    "&fsize=$(fsize)" +
                    "&mimeType=$(mimeType)" +
                    "&bucket=$(bucket)" +
                    "&avinfo=$(avinfo)";

            if (customData != null && !customData.isEmpty()) {
                StringBuilder sb = new StringBuilder(cbBody);
                for (Map.Entry<String, String> e : customData.entrySet()) {
                    sb.append("&").append(e.getKey()).append("=").append(e.getValue());
                }
                cbBody = sb.toString();
            }
            policy.put("callbackBody", cbBody);
            policy.put("callbackBodyType", "application/x-www-form-urlencoded");
        }

        policy.put("returnBody",
                "{\"key\":\"$(key)\",\"hash\":\"$(etag)\",\"fsize\":$(fsize)," +
                "\"mimeType\":\"$(mimeType)\",\"avinfo\":$(avinfo)}");

        String token = auth.uploadToken(config.getBucket(), null,
                config.getExpireSeconds(), policy);
        return UploadTokenResult.of(token, config.getDomain());
    }

    // ======================== 3. 断点续传 / 分片上传凭证 ========================

    public UploadTokenResult resumableUploadToken(String key, String mimeLimit) {
        StringMap policy = new StringMap();

        if (mimeLimit != null && !mimeLimit.isBlank()) {
            policy.put("mimeLimit", mimeLimit);
        }

        String effectiveKey = (key != null && !key.isBlank()) ? key : null;

        policy.put("returnBody",
                "{\"key\":\"$(key)\",\"hash\":\"$(etag)\"," +
                "\"fsize\":$(fsize),\"mimeType\":\"$(mimeType)\"}");

        String token = auth.uploadToken(config.getBucket(), effectiveKey,
                config.getExpireSeconds(), policy);
        return UploadTokenResult.of(token, config.getDomain());
    }

    // ======================== 4. 上传时自动触发转码 ========================

    public UploadTokenResult uploadTokenWithTranscoding(String fops, String outputKeyPrefix,
                                                        String notifyUrl) {
        StringMap policy = new StringMap();

        String entry = UrlSafeBase64.encodeToString(
                config.getBucket() + ":" + outputKeyPrefix + ".mp4");
        String persistentOps = fops + "|saveas/" + entry;

        policy.put("persistentOps", persistentOps);
        policy.put("persistentPipeline",
                config.getPersistentPipeline() != null ? config.getPersistentPipeline() : "");
        policy.put("persistentNotifyUrl",
                notifyUrl != null ? notifyUrl :
                        (config.getPersistentNotifyUrl() != null ? config.getPersistentNotifyUrl() : ""));

        policy.put("returnBody",
                "{\"key\":\"$(key)\",\"hash\":\"$(etag)\"," +
                "\"fsize\":$(fsize),\"persistentId\":\"$(persistentId)\"}");

        String token = auth.uploadToken(config.getBucket(), null,
                config.getExpireSeconds(), policy);
        return UploadTokenResult.of(token, config.getDomain());
    }

    // ======================== 5. 持久化处理（pfop） ========================

    /**
     * 对已上传的文件触发持久化处理（转码 / 截图 / 取封面等）。
     *
     * @param sourceKey 已上传的源文件 key
     * @param fops      处理指令，如 "avthumb/mp4/s/1280x720/vb/2000k"
     * @param outputKey 输出文件 key（不含 bucket）
     * @param notifyUrl 回调地址（可选）
     * @return persistentId
     */
    public String triggerTranscoding(String sourceKey, String fops,
                                     String outputKey, String notifyUrl) {
        try {
            String entry = UrlSafeBase64.encodeToString(
                    config.getBucket() + ":" + outputKey);
            String persistentOps = fops + "|saveas/" + entry;

            String effectiveNotifyUrl = notifyUrl != null ? notifyUrl
                    : config.getPersistentNotifyUrl();

            // pfop(bucket, key, fops, pipeline, notifyUrl)
            String persistentId = operationManager.pfop(
                    config.getBucket(), sourceKey, persistentOps,
                    config.getPersistentPipeline(), effectiveNotifyUrl);

            log.info("pfop triggered — sourceKey={}, outputKey={}, persistentId={}",
                    sourceKey, outputKey, persistentId);
            return persistentId;
        } catch (QiniuException e) {
            log.error("pfop failed — sourceKey={}, err={}", sourceKey, e.response, e);
            throw new RuntimeException("转码请求失败: " + e.getMessage(), e);
        }
    }

    /**
     * HLS 多码率转码（360p / 720p / 1080p）
     */
    public Map<String, String> triggerHlsTranscoding(String sourceKey, String outputPrefix,
                                                     String notifyUrl) {
        Map<String, String> results = new HashMap<>();

        String fops360 = "avthumb/m3u8/s/640x360/vb/600k";
        String key360 = outputPrefix + "_360p.m3u8";
        results.put("360p", triggerTranscoding(sourceKey, fops360, key360, notifyUrl));

        String fops720 = "avthumb/m3u8/s/1280x720/vb/2000k";
        String key720 = outputPrefix + "_720p.m3u8";
        results.put("720p", triggerTranscoding(sourceKey, fops720, key720, notifyUrl));

        String fops1080 = "avthumb/m3u8/s/1920x1080/vb/4000k";
        String key1080 = outputPrefix + "_1080p.m3u8";
        results.put("1080p", triggerTranscoding(sourceKey, fops1080, key1080, notifyUrl));

        return results;
    }

    /**
     * 截取视频封面（第 1 秒帧）
     */
    public String triggerSnapshot(String sourceKey, String outputKey, String notifyUrl) {
        String fops = "vframe/jpg/offset/1/w/480/h/360";
        return triggerTranscoding(sourceKey, fops, outputKey, notifyUrl);
    }

    // ======================== 6. 媒体元信息 ========================

    /**
     * 获取文件基本信息（大小、hash、MIME 等）
     */
    public FileInfo stat(String key) {
        try {
            return bucketManager.stat(config.getBucket(), key);
        } catch (QiniuException e) {
            log.error("stat failed — key={}, code={}", key, e.code(), e);
            throw new RuntimeException("获取文件信息失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取音视频元信息（时长/分辨率/码率/编码格式/流信息）。
     * 通过 HTTP GET {fileUrl}?avinfo 获取原始 JSON。
     */
    public String getAvInfo(String fileUrl) {
        try {
            String avinfoUrl = fileUrl + "?avinfo";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(avinfoUrl))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> resp = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());
            return resp.body();
        } catch (Exception e) {
            log.error("avinfo failed — url={}", fileUrl, e);
            throw new RuntimeException("获取媒体信息失败", e);
        }
    }

    // ======================== 7. 文件 URL ========================

    public String getFileUrl(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String domain = config.getDomain();
        if (domain.endsWith("/")) {
            return domain + key;
        }
        return domain + "/" + key;
    }

    public String getPrivateUrl(String key, long expireIn) {
        String publicUrl = getFileUrl(key);
        return auth.privateDownloadUrl(publicUrl, expireIn);
    }

    // ======================== 8. 文件管理 ========================

    public void delete(String key) {
        try {
            bucketManager.delete(config.getBucket(), key);
        } catch (QiniuException e) {
            log.error("delete failed — key={}", key, e);
            throw new RuntimeException("删除文件失败", e);
        }
    }

    public void move(String fromKey, String toKey) {
        try {
            bucketManager.move(config.getBucket(), fromKey, config.getBucket(), toKey);
        } catch (QiniuException e) {
            log.error("move failed — from={}, to={}", fromKey, toKey, e);
            throw new RuntimeException("移动文件失败", e);
        }
    }
}
