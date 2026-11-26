package cn.edu.scuec.icpc.lab.identity;

import cn.edu.scuec.icpc.lab.audit.AuditService;
import cn.edu.scuec.icpc.lab.common.ApiException;
import cn.edu.scuec.icpc.lab.security.ScopeGuard;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 角色与授权（权限矩阵 §3/§4）：授予必须显式写 scope 与原因，回收只置 revoked_at。
 */
@Service
public class RoleService {

    private final RoleRepository roleRepository;
    private final RoleAssignmentRepository assignmentRepository;
    private final PersonRepository personRepository;
    private final AccountService accountService;
    private final ScopeGuard scopeGuard;
    private final JdbcTemplate jdbcTemplate;
    private final AuditService auditService;

    public RoleService(RoleRepository roleRepository, RoleAssignmentRepository assignmentRepository,
                       PersonRepository personRepository, AccountService accountService,
                       ScopeGuard scopeGuard, JdbcTemplate jdbcTemplate, AuditService auditService) {
        this.roleRepository = roleRepository;
        this.assignmentRepository = assignmentRepository;
        this.personRepository = personRepository;
        this.accountService = accountService;
        this.scopeGuard = scopeGuard;
        this.jdbcTemplate = jdbcTemplate;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<RoleView> list() {
        scopeGuard.requireAnyScope("role:read");
        Map<String, RoleView> roles = new LinkedHashMap<>();
        jdbcTemplate.query("""
                select r.code, r.name, r.description, r.active, rp.permission_code
                  from role r
                  left join role_permission rp on rp.role_id = r.id
                 order by r.code, rp.permission_code
                """, rs -> {
            String code = rs.getString("code");
            RoleView view = roles.computeIfAbsent(code, key -> new RoleView(
                    key, rsSafe(rs, "name"), rsSafe(rs, "description"), true, new ArrayList<>()));
            String permission = rs.getString("permission_code");
            if (permission != null) {
                view.permissions().add(permission);
            }
        });
        return List.copyOf(roles.values());
    }

    private static String rsSafe(java.sql.ResultSet rs, String column) {
        try {
            return rs.getString(column);
        } catch (java.sql.SQLException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Transactional
    public AssignmentView assign(UUID personId, String roleCode, String scopeType, UUID scopeId, String reason) {
        scopeGuard.requireGlobal("role:assign");
        if (personRepository.findById(personId).isEmpty()) {
            throw ApiException.notFound("人员不存在");
        }
        RoleAssignment assignment = accountService.assignInternal(personId, roleCode, scopeType, scopeId, reason);
        auditService.record("role.assign", "role_assignment", assignment.getId(), null,
                Map.of("personId", personId.toString(), "roleCode", roleCode,
                        "scopeType", assignment.getScopeType(),
                        "scopeId", assignment.getScopeId() == null ? "" : assignment.getScopeId().toString()),
                reason);
        return view(assignment);
    }

    @Transactional(readOnly = true)
    public List<AssignmentView> assignments(UUID personId) {
        scopeGuard.requireAnyScope("role:read");
        return assignmentRepository.findByPersonIdAndRevokedAtIsNull(personId).stream()
                .map(this::view)
                .toList();
    }

    @Transactional
    public void revoke(UUID assignmentId, String reason) {
        accountService.revokeAssignment(assignmentId, reason);
    }

    private AssignmentView view(RoleAssignment assignment) {
        String roleCode = roleRepository.findById(assignment.getRoleId())
                .map(Role::getCode)
                .orElse("UNKNOWN");
        return new AssignmentView(
                assignment.getId(),
                assignment.getPersonId(),
                roleCode,
                assignment.getScopeType(),
                assignment.getScopeId(),
                assignment.getValidFrom(),
                assignment.getValidUntil(),
                assignment.getGrantedAt(),
                assignment.getReason());
    }

    public record RoleView(String code, String name, String description, boolean active,
                           List<String> permissions) {
    }

    public record AssignmentView(UUID id, UUID personId, String roleCode, String scopeType, UUID scopeId,
                                 java.time.Instant validFrom, java.time.Instant validUntil,
                                 java.time.Instant grantedAt, String reason) {
    }
}