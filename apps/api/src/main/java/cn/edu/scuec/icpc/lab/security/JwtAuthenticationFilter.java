package cn.edu.scuec.icpc.lab.security;

import cn.edu.scuec.icpc.lab.identity.Person;
import cn.edu.scuec.icpc.lab.identity.PersonRepository;
import cn.edu.scuec.icpc.lab.identity.UserAccount;
import cn.edu.scuec.icpc.lab.identity.UserAccountRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 每请求重新校验：令牌签名/有效期 → 账号存在且 ACTIVE → token_version 一致 → 现查权限。
 * 任一步失败都不建立认证上下文，受保护端点因此返回 401；账号停用后旧 access token 立即失效。
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";

    private final JwtService jwtService;
    private final UserAccountRepository accountRepository;
    private final PersonRepository personRepository;
    private final PermissionQuery permissionQuery;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            UserAccountRepository accountRepository,
            PersonRepository personRepository,
            PermissionQuery permissionQuery) {
        this.jwtService = jwtService;
        this.accountRepository = accountRepository;
        this.personRepository = personRepository;
        this.permissionQuery = permissionQuery;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER)) {
            chain.doFilter(request, response);
            return;
        }
        String token = header.substring(BEARER.length()).trim();
        jwtService.parse(token).flatMap(claims -> authenticate(claims))
                .ifPresent(authentication -> SecurityContextHolder.getContext().setAuthentication(authentication));
        chain.doFilter(request, response);
    }

    private java.util.Optional<UsernamePasswordAuthenticationToken> authenticate(JwtService.TokenClaims claims) {
        UserAccount account = accountRepository.findById(claims.accountId()).orElse(null);
        if (account == null || !account.isActive() || account.getTokenVersion() != claims.tokenVersion()) {
            return java.util.Optional.empty();
        }
        List<ScopeGrant> grants = permissionQuery.load(account.getPersonId());
        Set<String> permissions = permissionQuery.permissions(grants);
        String displayName = personRepository.findById(account.getPersonId())
                .map(Person::getName)
                .orElse(account.getUsername());
        AccountPrincipal principal = new AccountPrincipal(
                account.getId(),
                account.getPersonId(),
                account.getUsername(),
                displayName,
                account.getTokenVersion(),
                account.isMustChangePassword(),
                grants,
                permissions);
        List<SimpleGrantedAuthority> authorities = permissions.stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
        return java.util.Optional.of(
                new UsernamePasswordAuthenticationToken(principal, null, authorities));
    }
}