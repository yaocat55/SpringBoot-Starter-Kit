package com.quick.springbootqiniu.controller;

import com.qiniu.storage.model.FileInfo;
import com.quick.springbootqiniu.model.PfopRequest;
import com.quick.springbootqiniu.model.UploadTokenResult;
import com.quick.springbootqiniu.service.QiniuService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/qiniu")
public class QiniuController {

    private static final Logger log = LoggerFactory.getLogger(QiniuController.class);

    private final QiniuService qiniuService;

    public QiniuController(QiniuService qiniuService) {
        this.qiniuService = qiniuService;
    }

    // ======================== 1. 简单上传凭证 ========================

    @GetMapping("/upload-token")
    public ResponseEntity<Map<String, Object>> getUploadToken() {
        UploadTokenResult token = qiniuService.simpleUploadToken();
        return ok(token.asMap());
    }

    // ======================== 2. 带回调的上传凭证 ========================

    @GetMapping("/upload-token/callback")
    public ResponseEntity<Map<String, Object>> getUploadTokenWithCallback(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String bizType) {
        Map<String, String> customData = new HashMap<>();
        if (userId != null) customData.put("userId", userId);
        if (bizType != null) customData.put("bizType", bizType);

        UploadTokenResult token = qiniuService.uploadTokenWithCallback(customData);
        return ok(token.asMap());
    }

    // ======================== 3. 断点续传凭证（视频专用） ========================

    @GetMapping("/upload-token/resumable")
    public ResponseEntity<Map<String, Object>> getResumableUploadToken(
            @RequestParam(required = false) String key,
            @RequestParam(required = false, defaultValue = "video/*;audio/*") String mimeLimit) {
        UploadTokenResult token = qiniuService.resumableUploadToken(key, mimeLimit);
        return ok(token.asMap());
    }

    // ======================== 4. 上传时自动转码 ========================

    @GetMapping("/upload-token/with-transcoding")
    public ResponseEntity<Map<String, Object>> getUploadTokenWithTranscoding(
            @RequestParam(defaultValue = "avthumb/mp4/s/1280x720/vb/2000k") String fops,
            @RequestParam String outputKeyPrefix) {
        UploadTokenResult token = qiniuService.uploadTokenWithTranscoding(
                fops, outputKeyPrefix, null);
        return ok(token.asMap());
    }

    // ======================== 5. 前端手动回调 ========================

    @PostMapping("/upload-callback")
    public ResponseEntity<Map<String, Object>> uploadCallback(
            @RequestParam String key,
            @RequestParam(required = false) String fileName,
            @RequestParam(required = false) Long fsize,
            @RequestParam(required = false) String mimeType) {

        String fileUrl = qiniuService.getFileUrl(key);

        // 伪代码: 保存到数据库
        // fileRecordMapper.insert(key, fileName, fsize, mimeType, fileUrl);

        Map<String, Object> data = new HashMap<>();
        data.put("key", key);
        data.put("fileName", fileName);
        data.put("fsize", fsize);
        data.put("mimeType", mimeType);
        data.put("url", fileUrl);

        return ok(data);
    }

    // ======================== 6. 七牛服务端回调 ========================

    /**
     * 七牛上传完成后回调此接口（需要公网可达）。
     * 回调参数由上传凭证中的 callbackBody 决定。
     */
    @PostMapping("/qiniu-callback")
    public ResponseEntity<Map<String, Object>> qiniuCallback(
            @RequestParam String key,
            @RequestParam String hash,
            @RequestParam long fsize,
            @RequestParam String mimeType,
            @RequestParam String bucket,
            @RequestParam(required = false) String avinfo,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String bizType) {

        String fileUrl = qiniuService.getFileUrl(key);

        log.info("qiniu callback — key={}, fsize={}, mimeType={}", key, fsize, mimeType);

        // 伪代码: 保存到数据库
        // fileRecordMapper.insert(key, hash, fsize, mimeType, bucket, fileUrl, avinfo, userId, bizType);

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("msg", "ok");
        return ResponseEntity.ok(result);
    }

    // ======================== 7. 转码操作 ========================

    @PostMapping("/pfop")
    public ResponseEntity<Map<String, Object>> triggerPfop(@RequestBody PfopRequest req) {
        String persistentId;
        if (req.getPipelineOps() != null && !req.getPipelineOps().isBlank()) {
            persistentId = qiniuService.triggerTranscoding(
                    req.getKey(), req.getPipelineOps(),
                    req.getOutputPrefix() != null ? req.getOutputPrefix() : req.getKey() + "_processed",
                    null);
        } else {
            persistentId = qiniuService.triggerTranscoding(
                    req.getKey(), req.getFops(), req.getOutputPrefix(), null);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("persistentId", persistentId);
        data.put("sourceKey", req.getKey());
        return ok(data);
    }

    @PostMapping("/pfop/hls")
    public ResponseEntity<Map<String, Object>> triggerHls(
            @RequestParam String key,
            @RequestParam String outputPrefix) {
        Map<String, String> persistentIds = qiniuService.triggerHlsTranscoding(
                key, outputPrefix, null);

        Map<String, Object> data = new HashMap<>();
        data.put("sourceKey", key);
        data.put("persistentIds", persistentIds);
        return ok(data);
    }

    @PostMapping("/pfop/snapshot")
    public ResponseEntity<Map<String, Object>> triggerSnapshot(
            @RequestParam String key,
            @RequestParam String outputKey) {
        String persistentId = qiniuService.triggerSnapshot(key, outputKey, null);

        Map<String, Object> data = new HashMap<>();
        data.put("persistentId", persistentId);
        data.put("coverKey", outputKey);
        data.put("coverUrl", qiniuService.getFileUrl(outputKey));
        return ok(data);
    }

    @PostMapping("/pfop-callback")
    public ResponseEntity<String> pfopCallback(@RequestBody String rawBody) {
        log.info("pfop callback received: {}", rawBody);
        return ResponseEntity.ok("ok");
    }

    // ======================== 8. 媒体信息 ========================

    @GetMapping("/stat")
    public ResponseEntity<Map<String, Object>> stat(@RequestParam String key) {
        FileInfo info = qiniuService.stat(key);
        Map<String, Object> data = new HashMap<>();
        data.put("key", key);
        data.put("fsize", info.fsize);
        data.put("hash", info.hash);
        data.put("mimeType", info.mimeType);
        data.put("putTime", info.putTime);
        data.put("url", qiniuService.getFileUrl(key));
        return ok(data);
    }

    @GetMapping("/avinfo")
    public ResponseEntity<Map<String, Object>> avInfo(@RequestParam String key) {
        String publicUrl = qiniuService.getFileUrl(key);
        String avinfo = qiniuService.getAvInfo(publicUrl);

        Map<String, Object> data = new HashMap<>();
        data.put("key", key);
        data.put("url", publicUrl);
        data.put("avinfo", avinfo);
        return ok(data);
    }

    // ======================== 9. 文件 URL ========================

    @GetMapping("/private-url")
    public ResponseEntity<Map<String, Object>> getPrivateUrl(
            @RequestParam String key,
            @RequestParam(defaultValue = "3600") long expireIn) {
        String signedUrl = qiniuService.getPrivateUrl(key, expireIn);
        Map<String, Object> data = new HashMap<>();
        data.put("key", key);
        data.put("url", signedUrl);
        data.put("expireIn", expireIn);
        return ok(data);
    }

    // ======================== 10. 文件管理 ========================

    @DeleteMapping("/delete")
    public ResponseEntity<Map<String, Object>> delete(@RequestParam String key) {
        qiniuService.delete(key);
        return ok(Map.of("key", key, "deleted", true));
    }

    // ======================== helper ========================

    private ResponseEntity<Map<String, Object>> ok(Object data) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("msg", "success");
        result.put("data", data);
        return ResponseEntity.ok(result);
    }
}
