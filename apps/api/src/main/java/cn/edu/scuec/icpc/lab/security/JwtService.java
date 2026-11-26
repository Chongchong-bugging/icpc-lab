package cn.edu.scuec.icpc.lab.security;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import org.springframework.stereotype.Component;

/**
 * 手写 HS256 JWT（不引第三方库，减少依赖面）。
 * 令牌只携带 accountId/tokenVersion；权限与状态每请求查库，保证"停用即失效"（TECH-12）。
 */
@Component
public class JwtService {

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private static final String HEADER = ENCODER.encodeToString(
            "{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));

    private final byte[] key;
    private final String issuer;
    private final Duration accessTtl;
    private final ObjectMapper objectMapper;

    public JwtService(
            cn.edu.scuec.icpc.lab.config.SecurityProperties properties,
            ObjectMapper objectMapper) {
        if (properties.jwtSecret() == null || properties.jwtSecret().length() < 32) {
            throw new IllegalStateException("app.security.jwt-secret 至少需要 32 个字符（TECH-12）");
        }
        this.key = properties.jwtSecret().getBytes(StandardCharsets.UTF_8);
        this.issuer = properties.issuer();
        this.accessTtl = properties.accessTtl();
        this.objectMapper = objectMapper;
    }

    public String issue(UUID accountId, long tokenVersion) {
        Instant now = Instant.now();
        ObjectNode claims = objectMapper.createObjectNode();
        claims.put("iss", issuer);
        claims.put("sub", accountId.toString());
        claims.put("ver", tokenVersion);
        claims.put("iat", now.getEpochSecond());
        claims.put("exp", now.plus(accessTtl).getEpochSecond());
        String payload = ENCODER.encodeToString(claims.toString().getBytes(StandardCharsets.UTF_8));
        String signingInput = HEADER + "." + payload;
        return signingInput + "." + ENCODER.encodeToString(sign(signingInput));
    }

    public Optional<TokenClaims> parse(String token) {
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            return Optional.empty();
        }
        String signingInput = parts[0] + "." + parts[1];
        byte[] expected = sign(signingInput);
        byte[] actual;
        try {
            actual = DECODER.decode(parts[2]);
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
        if (!MessageDigest.isEqual(expected, actual)) {
            return Optional.empty();
        }
        try {
            ObjectNode claims = (ObjectNode) objectMapper.readTree(DECODER.decode(parts[1]));
            if (!issuer.equals(claims.path("iss").asText())) {
                return Optional.empty();
            }
            if (claims.path("exp").asLong() <= Instant.now().getEpochSecond()) {
                return Optional.empty();
            }
            UUID accountId = UUID.fromString(claims.path("sub").asText());
            return Optional.of(new TokenClaims(accountId, claims.path("ver").asLong()));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private byte[] sign(String input) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(input.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("无法签发令牌", ex);
        }
    }

    public record TokenClaims(UUID accountId, long tokenVersion) {
    }
}