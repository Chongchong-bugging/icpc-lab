package cn.edu.scuec.icpc.lab.security;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 当前请求的登录主体。权限每请求从数据库装载，账号停用或 token_version 变更立即生效（NFR-02）。
 */
public class AccountPrincipal {

    private final UUID accountId;
    private final UUID personId;
    private final String username;
    private final String displayName;
    private final long tokenVersion;
    private final boolean mustChangePassword;
    private final List<ScopeGrant> grants;
    private final Set<String> permissions;

    public AccountPrincipal(
            UUID accountId,
            UUID personId,
            String username,
            String displayName,
            long tokenVersion,
            boolean mustChangePassword,
            List<ScopeGrant> grants,
            Set<String> permissions) {
        this.accountId = accountId;
        this.personId = personId;
        this.username = username;
        this.displayName = displayName;
        this.tokenVersion = tokenVersion;
        this.mustChangePassword = mustChangePassword;
        this.grants = List.copyOf(grants);
        this.permissions = Set.copyOf(permissions);
    }

    public UUID accountId() {
        return accountId;
    }

    public UUID personId() {
        return personId;
    }

    public String username() {
        return username;
    }

    public String displayName() {
        return displayName;
    }

    public long tokenVersion() {
        return tokenVersion;
    }

    public boolean mustChangePassword() {
        return mustChangePassword;
    }

    public List<ScopeGrant> grants() {
        return grants;
    }

    public Set<String> permissions() {
        return permissions;
    }

    public boolean hasPermission(String permission) {
        return permissions.contains(permission);
    }
}