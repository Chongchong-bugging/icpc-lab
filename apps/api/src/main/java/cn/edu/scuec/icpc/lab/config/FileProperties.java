package cn.edu.scuec.icpc.lab.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 附件存储配置。默认目录已被 .gitignore 排除，避免把上传物提交入库。
 */
@ConfigurationProperties(prefix = "app.file")
public record FileProperties(String storageDir, long maxSizeBytes) {
}