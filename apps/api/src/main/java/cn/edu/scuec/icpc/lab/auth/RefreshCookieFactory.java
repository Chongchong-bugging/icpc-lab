package cn.edu.scuec.icpc.lab.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import cn.edu.scuec.icpc.lab.config.SecurityProperties;

/**
 * 刷新令牌 Cookie 的唯一构造点，避免 HttpOnly/SameSite 配置散落。
 */
@Service
public class RefreshCookieFactory {

    private final SecurityProperties properties;

    public RefreshCookieFactory(SecurityProperties properties) {
        this.properties = properties;
    }

    public ResponseCookie create(String rawToken) {
        return base(rawToken, properties.refreshTtl());
    }

    public ResponseCookie clear() {
        return base("", Duration.ZERO);
    }

    private ResponseCookie base(String value, Duration maxAge) {
        return ResponseCookie.from(properties.refreshCookieName(), value)
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite(properties.cookieSameSite())
                .path("/api/v1/auth")
                .maxAge(maxAge)
                .build();
    }

    public String cookieName() {
        return properties.refreshCookieName();
    }

    /** 去重后的角色码，按首次出现顺序返回。 */
    public static List<String> roles(List<cn.edu.scuec.icpc.lab.security.ScopeGrant> grants) {
        Set<String> codes = new LinkedHashSet<>();
        grants.forEach(grant -> codes.add(grant.roleCode()));
        return List.copyOf(codes);
    }

    public static Instant now() {
        return Instant.now();
    }
}