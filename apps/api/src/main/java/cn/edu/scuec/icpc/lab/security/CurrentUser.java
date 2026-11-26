package cn.edu.scuec.icpc.lab.security;

import cn.edu.scuec.icpc.lab.common.ApiException;
import cn.edu.scuec.icpc.lab.common.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 当前登录主体与请求元数据（供审计使用）。
 */
@Component
public class CurrentUser {

    public AccountPrincipal principal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AccountPrincipal principal)) {
            throw ApiException.unauthorized("UNAUTHENTICATED", "未登录或登录已过期");
        }
        return principal;
    }

    public AccountPrincipal principalOrNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AccountPrincipal principal)) {
            return null;
        }
        return principal;
    }

    public String requestId() {
        return MDC.get(RequestIdFilter.MDC_KEY);
    }

    public String ip() {
        HttpServletRequest request = request();
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    public String userAgent() {
        HttpServletRequest request = request();
        return request == null ? null : request.getHeader("User-Agent");
    }

    private HttpServletRequest request() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }
}