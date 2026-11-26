package cn.edu.scuec.icpc.lab.auth;

import cn.edu.scuec.icpc.lab.security.CurrentUser;
import cn.edu.scuec.icpc.lab.security.PublicEndpoint;
import cn.edu.scuec.icpc.lab.security.PermissionCode;
import cn.edu.scuec.icpc.lab.security.PermissionQuery;
import cn.edu.scuec.icpc.lab.security.ScopeGrant;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final RefreshCookieFactory cookieFactory;
    private final CurrentUser currentUser;
    private final PermissionQuery permissionQuery;

    public AuthController(AuthService authService, RefreshCookieFactory cookieFactory,
                          CurrentUser currentUser, PermissionQuery permissionQuery) {
        this.authService = authService;
        this.cookieFactory = cookieFactory;
        this.currentUser = currentUser;
        this.permissionQuery = permissionQuery;
    }

    @PublicEndpoint
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                               HttpServletRequest httpRequest) {
        AuthService.LoginResult result = authService.login(request.username(), request.password(),
                currentUser.ip(), currentUser.userAgent());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieFactory.create(result.refreshToken()).toString())
                .body(toResponse(result));
    }

    @PublicEndpoint
    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(HttpServletRequest request) {
        AuthService.LoginResult result = authService.refresh(readRefreshCookie(request),
                currentUser.ip(), currentUser.userAgent());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieFactory.create(result.refreshToken()).toString())
                .body(toResponse(result));
    }

    @PublicEndpoint
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        authService.logout(readRefreshCookie(request));
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookieFactory.clear().toString())
                .build();
    }

    @PreAuthorize("hasAuthority('profile:read:self')")
    @GetMapping("/me")
    public ResponseEntity<MeResponse> me() {
        var principal = currentUser.principal();
        List<String> roles = principal.grants().stream().map(ScopeGrant::roleCode).distinct().toList();
        List<String> permissions = permissionQuery.permissions(principal.grants()).stream().toList();
        return ResponseEntity.ok(new MeResponse(
                principal.accountId(),
                principal.personId(),
                principal.username(),
                principal.displayName(),
                roles,
                permissions,
                principal.mustChangePassword(),
                PermissionCode.values().length));
    }

    private LoginResponse toResponse(AuthService.LoginResult result) {
        return new LoginResponse(
                result.accessToken(),
                "Bearer",
                result.expiresInSeconds(),
                result.mustChangePassword(),
                result.user());
    }

    private String readRefreshCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        String name = cookieFactory.cookieName();
        return Arrays.stream(cookies)
                .filter(cookie -> cookie.getName().equals(name))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    public record MeResponse(
            java.util.UUID accountId,
            java.util.UUID personId,
            String username,
            String displayName,
            List<String> roles,
            List<String> permissions,
            boolean mustChangePassword,
            int permissionCatalogSize) {
    }
}