package com.quick.springbootqiniu.model;

/**
 * 前端发起转码请求的参数体
 */
public class PfopRequest {

    /** 已上传的源文件 key */
    private String key;

    /** 转码后的文件名前缀，如 "video/hls/abc" */
    private String outputPrefix;

    /**
     * 转码模板，如：
     * <pre>
     * avthumb/mp4/s/1280x720/vb/2000k              — 转 MP4 720p
     * avthumb/m3u8/s/640x360/vb/600k               — 转 HLS 360p
     * vframe/jpg/offset/1/w/480/h/360              — 截取第 1 秒作为封面
     * </pre>
     */
    private String fops;

    /**
     * 多规格转码时可一次提交多个 fop，用分号分隔：
     * "avthumb/mp4/s/1280x720|saveas/<encodedEntry>;avthumb/mp4/s/640x360|saveas/<encodedEntry>"
     * <p>
     * 设置此字段后 fops/outputPrefix 失效，直接透传给七牛。
     */
    private String pipelineOps;

    // getters & setters
    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public String getOutputPrefix() { return outputPrefix; }
    public void setOutputPrefix(String outputPrefix) { this.outputPrefix = outputPrefix; }

    public String getFops() { return fops; }
    public void setFops(String fops) { this.fops = fops; }

    public String getPipelineOps() { return pipelineOps; }
    public void setPipelineOps(String pipelineOps) { this.pipelineOps = pipelineOps; }
}
