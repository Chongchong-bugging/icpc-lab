package cn.edu.scuec.icpc.lab.security;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 权限装载：角色 → 权限点 + 授予范围。
 * 只认未回收、在有效期内的 role_assignment；角色停用即失去其全部权限（权限矩阵 §2）。
 */
@Component
public class PermissionQuery {

    private static final String SQL = """
            select r.code as role_code, rp.permission_code as permission_code,
                   ra.scope_type as scope_type, ra.scope_id as scope_id
              from role_assignment ra
              join role r on r.id = ra.role_id
              join role_permission rp on rp.role_id = ra.role_id
             where ra.person_id = ?
               and ra.revoked_at is null
               and r.active = true
               and (ra.valid_from is null or ra.valid_from <= ?)
               and (ra.valid_until is null or ra.valid_until > ?)
             order by r.code, rp.permission_code
            """;

    private final JdbcTemplate jdbcTemplate;

    public PermissionQuery(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ScopeGrant> load(UUID personId) {
        Instant now = Instant.now();
        Timestamp ts = Timestamp.from(now);
        List<ScopeGrant> grants = new ArrayList<>();
        jdbcTemplate.query(SQL, rs -> {
            String roleCode = rs.getString("role_code");
            String permission = rs.getString("permission_code");
            ScopeType scopeType = ScopeType.valueOf(rs.getString("scope_type"));
            UUID scopeId = rs.getObject("scope_id", UUID.class);
            grants.add(new ScopeGrant(roleCode, permission, scopeType, scopeId));
        }, personId, ts, ts);
        return grants;
    }

    public Set<String> permissions(List<ScopeGrant> grants) {
        Set<String> codes = new LinkedHashSet<>();
        for (ScopeGrant grant : grants) {
            codes.add(grant.permission());
        }
        return codes;
    }
}