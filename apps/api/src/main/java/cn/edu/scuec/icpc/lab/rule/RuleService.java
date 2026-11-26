package cn.edu.scuec.icpc.lab.rule;

import cn.edu.scuec.icpc.lab.audit.AuditService;
import cn.edu.scuec.icpc.lab.common.ApiException;
import cn.edu.scuec.icpc.lab.security.ScopeGuard;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 规则键维护（DOM-12 / I-23 / WP-P0-02）：
 * 未确认阈值以 value=null + mode=MANUAL 存储，只能提示或生成人工待办，不得自动处罚。
 */
@Service
public class RuleService {

    private static final Set<String> MODES = Set.of("BLOCK", "WARN", "MANUAL");

    private final JdbcTemplate jdbcTemplate;
    private final ScopeGuard scopeGuard;
    private final AuditService auditService;

    public RuleService(JdbcTemplate jdbcTemplate, ScopeGuard scopeGuard, AuditService auditService) {
        this.jdbcTemplate = jdbcTemplate;
        this.scopeGuard = scopeGuard;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<RuleView> list() {
        scopeGuard.requireAnyScope("rule:read");
        return jdbcTemplate.query("""
                select rule_key, rule_value, mode, description, version, source_clause, updated_at
                  from rule_config
                 order by rule_key
                """, (rs, rowNum) -> view(rs));
    }

    @Transactional
    public RuleView update(String key, String value, String mode, String sourceClause, String reason) {
        scopeGuard.requireGlobal("rule:write");
        String normalizedMode = mode == null ? null : mode.toUpperCase();
        if (normalizedMode == null || !MODES.contains(normalizedMode)) {
            throw ApiException.badRequest("INVALID_RULE_MODE", "mode 只能是 BLOCK / WARN / MANUAL");
        }
        String normalizedValue = value == null || value.isBlank() ? null : value.trim();
        if ("MANUAL".equals(normalizedMode) && normalizedValue != null) {
            throw ApiException.badRequest("MANUAL_RULE_MUST_BE_NULL",
                    "未确认阈值必须以 MANUAL + 空值保存，禁止自动判定（DOM-12）");
        }
        if (!"MANUAL".equals(normalizedMode) && normalizedValue == null) {
            throw ApiException.badRequest("RULE_VALUE_REQUIRED", "该模式必须提供阈值");
        }
        if (normalizedValue != null && normalizedValue.length() > 255) {
            throw ApiException.badRequest("RULE_VALUE_TOO_LONG", "阈值长度不能超过 255");
        }
        RuleView before = find(key);
        jdbcTemplate.update("""
                update rule_config
                   set rule_value = ?, mode = ?, source_clause = ?, version = version + 1, updated_at = current_timestamp
                 where rule_key = ?
                """, normalizedValue, normalizedMode, sourceClause, key);
        RuleView after = find(key);
        auditService.record("rule.update", "rule_config", key, before, after, reason);
        return after;
    }

    private RuleView find(String key) {
        List<RuleView> rows = jdbcTemplate.query("""
                select rule_key, rule_value, mode, description, version, source_clause, updated_at
                  from rule_config
                 where rule_key = ?
                """, (rs, rowNum) -> view(rs), key);
        if (rows.isEmpty()) {
            throw ApiException.notFound("规则键不存在：" + key);
        }
        return rows.get(0);
    }

    private static RuleView view(ResultSet rs) throws SQLException {
        Instant updatedAt = rs.getTimestamp("updated_at") == null ? null : rs.getTimestamp("updated_at").toInstant();
        return new RuleView(
                rs.getString("rule_key"),
                rs.getString("rule_value"),
                rs.getString("mode"),
                rs.getString("description"),
                rs.getInt("version"),
                rs.getString("source_clause"),
                updatedAt);
    }

    public record RuleView(String key, String value, String mode, String description,
                           int version, String sourceClause, Instant updatedAt) {
    }

    public record UpdateRequest(String value, String mode, String sourceClause, String reason) {
    }
}