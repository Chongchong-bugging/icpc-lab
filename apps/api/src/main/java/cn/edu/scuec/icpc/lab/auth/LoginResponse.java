package cn.edu.scuec.icpc.lab.auth;

/**
 * 登录/刷新响应：access token 只放响应体，由前端保存在内存（禁止 localStorage，TECH-12）；
 * refresh token 走 HttpOnly Cookie，前端不可读。
 */
public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds,
        boolean mustChangePassword,
        AuthService.UserView user) {
}