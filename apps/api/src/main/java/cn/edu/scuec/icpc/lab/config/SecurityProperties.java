package cn.edu.scuec.icpc.lab.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 认证与账号锁定参数（TECH-12 / NFR-02）。
 * 生产密钥与 Cookie 安全属性由 application-prod.yml 强制覆盖。
 */
@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(
        String jwtSecret,
        String issuer,
        Duration accessTtl,
        Duration refreshTtl,
        String refreshCookieName,
        boolean cookieSecure,
        String cookieSameSite,
        int maxFailedAttempts,
        Duration lockDuration) {
}