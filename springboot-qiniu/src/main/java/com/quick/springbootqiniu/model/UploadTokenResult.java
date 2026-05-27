package com.quick.springbootqiniu.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 上传凭证返回体，前端按 token / domain / uploadUrl 字段消费即可
 */
public class UploadTokenResult {

    /** 上传凭证，前端塞进 formData 的 token 字段 */
    private String token;

    /** CDN 域名，用于拼接最终访问 URL */
    private String domain;

    /** 七牛上传入口（默认 https://upload.qiniup.com，分片上传也用此地址） */
    private String uploadUrl;

    public static UploadTokenResult of(String token, String domain) {
        UploadTokenResult r = new UploadTokenResult();
        r.token = token;
        r.domain = domain;
        r.uploadUrl = "https://upload.qiniup.com";
        return r;
    }

    public Map<String, Object> asMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("token", token);
        map.put("domain", domain);
        map.put("uploadUrl", uploadUrl);
        return map;
    }

    // getters & setters
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }

    public String getUploadUrl() { return uploadUrl; }
    public void setUploadUrl(String uploadUrl) { this.uploadUrl = uploadUrl; }
}
