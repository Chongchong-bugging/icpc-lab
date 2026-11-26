package cn.edu.scuec.icpc.lab.auth;

import cn.edu.scuec.icpc.lab.common.ApiException;
import cn.edu.scuec.icpc.lab.config.SecurityProperties;
import cn.edu.scuec.icpc.lab.identity.Person;
import cn.edu.scuec.icpc.lab.identity.PersonRepository;
import cn.edu.scuec.icpc.lab.identity.UserAccount;
import cn.edu.scuec.icpc.lab.identity.UserAccountRepository;
import cn.edu.scuec.icpc.lab.security.JwtService;
import cn.edu.scuec.icpc.lab.security.PermissionQuery;
import cn.edu.scuec.icpc.lab.security.ScopeGrant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 登录/刷新/登出（WP-P0-03 / TECH-12 / NFR-02）。
 * - 失败累计到上限即锁定一段时间；
 * - 刷新令牌只存哈希、单次使用、同 family 轮换，重放即整族撤销；
 * - 停用/改密通过 token_version 让全部已签发令牌立即失效。
 */
@Service
public class AuthService {

    private final UserAccountRepository accountRepository;
    private final PersonRepository personRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final PermissionQuery permissionQuery;
    private final SecurityProperties properties;
    private final LoginAttemptService loginAttemptService;
    private final SecureRandom random = new SecureRandom();

    public AuthService(
            UserAccountRepository accountRepository,
            PersonRepository personRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            PermissionQuery permissionQuery,
            SecurityProperties properties,
            LoginAttemptService loginAttemptService) {
        this.accountRepository = accountRepository;
        this.personRepository = personRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.permissionQuery = permissionQuery;
        this.properties = properties;
        this.loginAttemptService = loginAttemptService;
    }

    @Transactional
    public LoginResult login(String username, String password, String ip, String userAgent) {
        UserAccount account = accountRepository.findByUsername(username)
                .orElseThrow(() -> ApiException.unauthorized("INVALID_CREDENTIALS", "用户名或密码不正确"));
        if (!account.isActive()) {
            throw ApiException.forbidden("ACCOUNT_DISABLED", "账号已停用，请联系管理员");
        }
        Instant now = Instant.now();
        if (account.getLockedUntil() != null && account.getLockedUntil().isAfter(now)) {
            throw new ApiException(HttpStatus.LOCKED, "ACCOUNT_LOCKED", "失败次数过多，账号暂时锁定");
        }
        if (!passwordEncoder.matches(password, account.getPasswordHash())) {
            loginAttemptService.registerFailure(account.getId(),
                    properties.maxFailedAttempts(), properties.lockDuration());
            throw ApiException.unauthorized("INVALID_CREDENTIALS", "用户名或密码不正确");
        }
        account.setFailedAttempts(0);
        account.setLockedUntil(null);
        account.setLastLoginAt(now);
        accountRepository.save(account);
        return issue(account, UUID.randomUUID(), ip, userAgent);
    }

    @Transactional
    public LoginResult refresh(String rawToken, String ip, String userAgent) {
        if (rawToken == null || rawToken.isBlank()) {
            throw ApiException.unauthorized("REFRESH_REQUIRED", "缺少刷新令牌");
        }
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> ApiException.unauthorized("INVALID_REFRESH_TOKEN", "刷新令牌无效"));
        if (stored.getRevokedAt() != null) {
            // 已使用/已撤销的令牌被重放：整族撤销，要求重新登录
            revokeFamily(stored.getFamilyId());
            throw ApiException.unauthorized("REFRESH_TOKEN_REUSED", "刷新令牌已失效，请重新登录");
        }
        UserAccount account = accountRepository.findById(stored.getAccountId())
                .orElseThrow(() -> ApiException.unauthorized("INVALID_REFRESH_TOKEN", "刷新令牌无效"));
        if (!account.isActive() || account.getTokenVersion() != stored.getTokenVersion()) {
            revokeFamily(stored.getFamilyId());
            throw ApiException.unauthorized("SESSION_REVOKED", "会话已失效，请重新登录");
        }
        if (stored.getExpiresAt().isBefore(Instant.now())) {
            throw ApiException.unauthorized("REFRESH_EXPIRED", "刷新令牌已过期，请重新登录");
        }
        LoginResult result = issue(account, stored.getFamilyId(), ip, userAgent);
        stored.setRevokedAt(Instant.now());
        refreshTokenRepository.save(stored);
        return result;
    }

    @Transactional
    public void logout(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(hash(rawToken))
                .ifPresent(token -> revokeFamily(token.getFamilyId()));
    }

    private void revokeFamily(UUID familyId) {
        Instant now = Instant.now();
        for (RefreshToken token : refreshTokenRepository.findByFamilyIdAndRevokedAtIsNull(familyId)) {
            token.setRevokedAt(now);
            refreshTokenRepository.save(token);
        }
    }

    private LoginResult issue(UserAccount account, UUID familyId, String ip, String userAgent) {
        String accessToken = jwtService.issue(account.getId(), account.getTokenVersion());
        String rawRefresh = newRefreshToken();
        RefreshToken token = new RefreshToken();
        token.setAccountId(account.getId());
        token.setFamilyId(familyId);
        token.setTokenHash(hash(rawRefresh));
        token.setTokenVersion(account.getTokenVersion());
        token.setIssuedAt(Instant.now());
        token.setExpiresAt(Instant.now().plus(properties.refreshTtl()));
        token.setIp(ip);
        token.setUserAgent(trim(userAgent));
        refreshTokenRepository.save(token);

        Person person = personRepository.findById(account.getPersonId()).orElse(null);
        List<ScopeGrant> grants = permissionQuery.load(account.getPersonId());
        return new LoginResult(
                accessToken,
                properties.accessTtl().toSeconds(),
                account.isMustChangePassword(),
                buildUser(account, person, grants),
                rawRefresh,
                token.getExpiresAt());
    }

    private UserView buildUser(UserAccount account, Person person, List<ScopeGrant> grants) {
        return new UserView(
                account.getId(),
                account.getPersonId(),
                account.getUsername(),
                person == null ? account.getUsername() : person.getName(),
                RefreshCookieFactory.roles(grants),
                grants.stream().map(ScopeGrant::permission).distinct().toList(),
                account.isMustChangePassword());
    }

    private String newRefreshToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("无法计算令牌摘要", ex);
        }
    }

    private static String trim(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 255 ? value : value.substring(0, 255);
    }

    public record LoginResult(
            String accessToken,
            long expiresInSeconds,
            boolean mustChangePassword,
            UserView user,
            String refreshToken,
            Instant refreshExpiresAt) {
    }

    public record UserView(
            UUID accountId,
            UUID personId,
            String username,
            String displayName,
            List<String> roles,
            List<String> permissions,
            boolean mustChangePassword) {
    }
}