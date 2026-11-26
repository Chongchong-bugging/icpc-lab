package cn.edu.scuec.icpc.lab.file;

import cn.edu.scuec.icpc.lab.common.ApiException;
import cn.edu.scuec.icpc.lab.config.SecurityProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * 短时下载签名（TECH-13 / DATA-12）：链接绑定文件、用户与过期时间，离开服务端无法伪造。
 * 默认 5 分钟有效；过期或签名不符一律 401，不泄露文件是否存在。
 */
@Component
public class FileTokenService {

    private static final long TTL_SECONDS = 300;
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final byte[] key;

    public FileTokenService(SecurityProperties properties) {
        this.key = ("file-token:" + properties.jwtSecret()).getBytes(StandardCharsets.UTF_8);
    }

    public String issue(UUID fileId, UUID accountId) {
        long expiresAt = Instant.now().getEpochSecond() + TTL_SECONDS;
        String payload = fileId + ":" + expiresAt + ":" + accountId;
        String encodedPayload = ENCODER.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return encodedPayload + "." + ENCODER.encodeToString(sign(encodedPayload));
    }

    public UUID verify(String token) {
        if (token == null || token.isBlank()) {
            throw ApiException.unauthorized("INVALID_DOWNLOAD_TOKEN", "下载链接无效");
        }
        String[] parts = token.split("\\.");
        if (parts.length != 2) {
            throw ApiException.unauthorized("INVALID_DOWNLOAD_TOKEN", "下载链接无效");
        }
        byte[] expected = sign(parts[0]);
        byte[] actual = DECODER.decode(parts[1]);
        if (!MessageDigest.isEqual(expected, actual)) {
            throw ApiException.unauthorized("INVALID_DOWNLOAD_TOKEN", "下载链接无效");
        }
        String payload = new String(DECODER.decode(parts[0]), StandardCharsets.UTF_8);
        String[] fields = payload.split(":");
        if (fields.length != 3) {
            throw ApiException.unauthorized("INVALID_DOWNLOAD_TOKEN", "下载链接无效");
        }
        if (Long.parseLong(fields[1]) <= Instant.now().getEpochSecond()) {
            throw ApiException.unauthorized("DOWNLOAD_TOKEN_EXPIRED", "下载链接已过期，请重新获取");
        }
        return UUID.fromString(fields[0]);
    }

    private byte[] sign(String input) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(input.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("无法签发下载链接", ex);
        }
    }
}