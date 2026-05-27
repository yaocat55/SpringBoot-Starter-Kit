package com.quick.springbootqiniu.config;

import com.qiniu.processing.OperationManager;
import com.qiniu.storage.BucketManager;
import com.qiniu.storage.UploadManager;
import com.qiniu.util.Auth;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "qiniu")
public class QiniuConfig {

    private String accessKey;
    private String secretKey;
    private String bucket;
    private String domain;
    private long expireSeconds = 3600;

    /** 七牛云上传回调地址（公网可达） */
    private String callbackUrl;

    /** 持久化处理队列名称（音视频转码用） */
    private String persistentPipeline;

    /** 转码完成回调地址 */
    private String persistentNotifyUrl;

    // ==================== Bean 定义 ====================

    @Bean
    public Auth qiniuAuth() {
        return Auth.create(accessKey, secretKey);
    }

    @Bean
    public UploadManager uploadManager() {
        com.qiniu.storage.Configuration cfg = new com.qiniu.storage.Configuration(
                com.qiniu.storage.Region.autoRegion()
        );
        return new UploadManager(cfg);
    }

    @Bean
    public BucketManager bucketManager(Auth auth) {
        com.qiniu.storage.Configuration cfg = new com.qiniu.storage.Configuration(
                com.qiniu.storage.Region.autoRegion()
        );
        return new BucketManager(auth, cfg);
    }

    @Bean
    public OperationManager operationManager(Auth auth) {
        com.qiniu.storage.Configuration cfg = new com.qiniu.storage.Configuration(
                com.qiniu.storage.Region.autoRegion()
        );
        return new OperationManager(auth, cfg);
    }

    // ==================== getters & setters ====================

    public String getAccessKey() { return accessKey; }
    public void setAccessKey(String accessKey) { this.accessKey = accessKey; }

    public String getSecretKey() { return secretKey; }
    public void setSecretKey(String secretKey) { this.secretKey = secretKey; }

    public String getBucket() { return bucket; }
    public void setBucket(String bucket) { this.bucket = bucket; }

    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }

    public long getExpireSeconds() { return expireSeconds; }
    public void setExpireSeconds(long expireSeconds) { this.expireSeconds = expireSeconds; }

    public String getCallbackUrl() { return callbackUrl; }
    public void setCallbackUrl(String callbackUrl) { this.callbackUrl = callbackUrl; }

    public String getPersistentPipeline() { return persistentPipeline; }
    public void setPersistentPipeline(String persistentPipeline) { this.persistentPipeline = persistentPipeline; }

    public String getPersistentNotifyUrl() { return persistentNotifyUrl; }
    public void setPersistentNotifyUrl(String persistentNotifyUrl) { this.persistentNotifyUrl = persistentNotifyUrl; }
}
