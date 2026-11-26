package cn.edu.scuec.icpc.lab.identity;

import cn.edu.scuec.icpc.lab.audit.AuditService;
import cn.edu.scuec.icpc.lab.auth.RefreshToken;
import cn.edu.scuec.icpc.lab.auth.RefreshTokenRepository;
import cn.edu.scuec.icpc.lab.common.ApiException;
import cn.edu.scuec.icpc.lab.common.PageResponse;
import cn.edu.scuec.icpc.lab.security.CurrentUser;
import cn.edu.scuec.icpc.lab.security.ScopeGuard;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 账号管理（仅 SYSADMIN，权限矩阵 account:* / role:*）。
 * 停用、重置密码都会递增 token_version 并撤销刷新令牌，已签发 access token 立即失效（NFR-02）。
 */
@Service
public class AccountService {

    private final UserAccountRepository accountRepository;
    private final PersonRepository personRepository;
    private final RoleRepository roleRepository;
    private final RoleAssignmentRepository assignmentRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final ScopeGuard scopeGuard;
    private final CurrentUser currentUser;
    private final AuditService auditService;

    public AccountService(UserAccountRepository accountRepository, PersonRepository personRepository,
                          RoleRepository roleRepository, RoleAssignmentRepository assignmentRepository,
                          RefreshTokenRepository refreshTokenRepository, PasswordEncoder passwordEncoder,
                          ScopeGuard scopeGuard, CurrentUser currentUser, AuditService auditService) {
        this.accountRepository = accountRepository;
        this.personRepository = personRepository;
        this.roleRepository = roleRepository;
        this.assignmentRepository = assignmentRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.scopeGuard = scopeGuard;
        this.currentUser = currentUser;
        this.auditService = auditService;
    }

    @Transactional
    public AccountView create(CreateAccountRequest request) {
        scopeGuard.requireGlobal("account:write");
        String username = request.username().trim().toLowerCase(Locale.ROOT);
        if (username.isBlank() || username.length() > 64) {
            throw ApiException.badRequest("INVALID_USERNAME", "用户名长度需在 1-64 之间");
        }
        if (accountRepository.findByUsername(username).isPresent()) {
            throw ApiException.conflict("USERNAME_TAKEN", "用户名已存在");
        }
        Person person = resolvePerson(request);
        if (accountRepository.findByPersonId(person.getId()).isPresent()) {
            throw ApiException.conflict("ACCOUNT_EXISTS", "该人员已有账号");
        }
        UserAccount account = new UserAccount();
        account.setPersonId(person.getId());
        account.setUsername(username);
        account.setPasswordHash(passwordEncoder.encode(request.password()));
        account.setStatus(UserAccount.ACTIVE);
        account.setTokenVersion(1);
        account.setMustChangePassword(true);
        accountRepository.save(account);

        List<RoleBinding> bindings = request.roles() == null ? List.of() : request.roles();
        for (RoleBinding binding : bindings) {
            assignInternal(person.getId(), binding.roleCode(), binding.scopeType(), binding.scopeId(), "创建账号时授予");
        }
        auditService.record("account.create", "user_account", account.getId(), null,
                Map.of("username", username, "personId", person.getId().toString(),
                        "roles", bindings.stream().map(RoleBinding::roleCode).toList()),
                request.reason());
        return view(account);
    }

    private Person resolvePerson(CreateAccountRequest request) {
        if (request.personId() != null) {
            return personRepository.findById(request.personId())
                    .orElseThrow(() -> ApiException.notFound("人员不存在"));
        }
        if (request.name() == null || request.name().isBlank()) {
            throw ApiException.badRequest("NAME_REQUIRED", "新建人员必须提供姓名");
        }
        Person person = new Person();
        person.setName(request.name().trim());
        person.setStudentNo(blankToNull(request.studentNo()));
        person.setGender("UNKNOWN");
        return personRepository.save(person);
    }

    @Transactional(readOnly = true)
    public PageResponse<AccountView> list(String query, int page, int size) {
        scopeGuard.requireGlobal("account:read");
        List<AccountView> all = accountRepository.findAll(Sort.by(Sort.Direction.ASC, "username")).stream()
                .map(this::view)
                .filter(view -> query == null || query.isBlank()
                        || view.username().contains(query.toLowerCase(Locale.ROOT))
                        || view.displayName().contains(query))
                .toList();
        int from = Math.min(Math.max(page, 0) * Math.max(size, 1), all.size());
        int to = Math.min(from + Math.max(size, 1), all.size());
        return new PageResponse<>(all.subList(from, to), Math.max(page, 0), Math.max(size, 1), all.size());
    }

    @Transactional
    public AccountView disable(UUID accountId, String reason) {
        scopeGuard.requireGlobal("account:write");
        UserAccount account = requireAccount(accountId);
        assertNotSelf(account, "不得停用本人账号");
        account.setStatus(UserAccount.DISABLED);
        bumpAndRevoke(account);
        auditService.record("account.disable", "user_account", accountId, Map.of("status", "ACTIVE"),
                Map.of("status", "DISABLED"), reason);
        return view(account);
    }

    @Transactional
    public AccountView enable(UUID accountId, String reason) {
        scopeGuard.requireGlobal("account:write");
        UserAccount account = requireAccount(accountId);
        account.setStatus(UserAccount.ACTIVE);
        account.setFailedAttempts(0);
        account.setLockedUntil(null);
        account.setTokenVersion(account.getTokenVersion() + 1);
        account.setLastLoginAt(account.getLastLoginAt());
        auditService.record("account.enable", "user_account", accountId, Map.of("status", "DISABLED"),
                Map.of("status", "ACTIVE"), reason);
        return view(account);
    }

    @Transactional
    public void resetPassword(UUID accountId, String newPassword, String reason) {
        scopeGuard.requireGlobal("account:reset");
        if (newPassword == null || newPassword.length() < 8 || newPassword.length() > 128) {
            throw ApiException.badRequest("WEAK_PASSWORD", "密码长度需在 8-128 之间");
        }
        UserAccount account = requireAccount(accountId);
        account.setPasswordHash(passwordEncoder.encode(newPassword));
        account.setMustChangePassword(true);
        bumpAndRevoke(account);
        auditService.record("account.password_reset", "user_account", accountId, null, null, reason);
    }

    private void bumpAndRevoke(UserAccount account) {
        account.setTokenVersion(account.getTokenVersion() + 1);
        accountRepository.save(account);
        List<RefreshToken> tokens = refreshTokenRepository.findAll().stream()
                .filter(token -> token.getAccountId().equals(account.getId()) && token.getRevokedAt() == null)
                .toList();
        for (RefreshToken token : tokens) {
            token.setRevokedAt(Instant.now());
            refreshTokenRepository.save(token);
        }
    }

    /** 供角色授予复用：校验角色存在且启用，按权限矩阵约束 scope 取值。 */
    @Transactional
    public RoleAssignment assignInternal(UUID personId, String roleCode, String scopeType, UUID scopeId,
                                         String reason) {
        Role role = roleRepository.findByCode(roleCode)
                .orElseThrow(() -> ApiException.badRequest("UNKNOWN_ROLE", "角色不存在：" + roleCode));
        if (!role.isActive()) {
            throw ApiException.badRequest("ROLE_DISABLED", "角色已停用：" + roleCode);
        }
        String normalizedScope = scopeType == null ? "SELF" : scopeType.toUpperCase(Locale.ROOT);
        if (!Set.of("SELF", "TEAM", "DEPARTMENT", "GLOBAL").contains(normalizedScope)) {
            throw ApiException.badRequest("INVALID_SCOPE", "数据范围取值不合法");
        }
        boolean needsScope = normalizedScope.equals("TEAM") || normalizedScope.equals("DEPARTMENT");
        if (needsScope && scopeId == null) {
            throw ApiException.badRequest("SCOPE_ID_REQUIRED", "该范围必须指定 scopeId");
        }
        if (!needsScope && scopeId != null) {
            throw ApiException.badRequest("SCOPE_ID_NOT_ALLOWED", "该范围不允许指定 scopeId");
        }
        boolean duplicated = assignmentRepository.findByPersonIdAndRevokedAtIsNull(personId).stream()
                .anyMatch(assignment -> assignment.getRoleId().equals(role.getId())
                        && assignment.getScopeType().equals(normalizedScope)
                        && java.util.Objects.equals(assignment.getScopeId(), scopeId));
        if (duplicated) {
            throw ApiException.conflict("ASSIGNMENT_EXISTS", "该角色与范围已授予");
        }
        RoleAssignment assignment = new RoleAssignment();
        assignment.setPersonId(personId);
        assignment.setRoleId(role.getId());
        assignment.setScopeType(normalizedScope);
        assignment.setScopeId(scopeId);
        assignment.setValidFrom(Instant.now());
        assignment.setGrantedBy(currentUser.principal().personId());
        assignment.setGrantedAt(Instant.now());
        assignment.setReason(reason);
        return assignmentRepository.save(assignment);
    }

    @Transactional
    public void revokeAssignment(UUID assignmentId, String reason) {
        scopeGuard.requireGlobal("role:assign");
        RoleAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> ApiException.notFound("授权记录不存在"));
        if (assignment.getRevokedAt() != null) {
            throw ApiException.conflict("ALREADY_REVOKED", "该授权已回收");
        }
        assignment.setRevokedAt(Instant.now());
        assignment.setRevokedBy(currentUser.principal().personId());
        assignment.setReason(reason);
        assignmentRepository.save(assignment);
        // 立即失效：递增该人员账号的 token_version（权限矩阵 §2）
        accountRepository.findByPersonId(assignment.getPersonId()).ifPresent(account -> {
            account.setTokenVersion(account.getTokenVersion() + 1);
            accountRepository.save(account);
        });
        auditService.record("role.revoke", "role_assignment", assignmentId,
                Map.of("roleId", assignment.getRoleId().toString(), "scope", assignment.getScopeType()), null, reason);
    }

    /**
     * 供任职结束等受信服务调用：不做权限判定（由调用方校验），但仍立即失效并审计。
     */
    @Transactional
    public void revokeInternal(UUID assignmentId, String reason) {
        RoleAssignment assignment = assignmentRepository.findById(assignmentId).orElse(null);
        if (assignment == null || assignment.getRevokedAt() != null) {
            return;
        }
        assignment.setRevokedAt(Instant.now());
        assignment.setRevokedBy(currentUser.principal().personId());
        assignment.setReason(reason);
        assignmentRepository.save(assignment);
        accountRepository.findByPersonId(assignment.getPersonId()).ifPresent(account -> {
            account.setTokenVersion(account.getTokenVersion() + 1);
            accountRepository.save(account);
        });
        auditService.record("role.revoke", "role_assignment", assignmentId,
                Map.of("roleId", assignment.getRoleId().toString(), "scope", assignment.getScopeType()),
                Map.of("revoked", true), reason);
    }

    private UserAccount requireAccount(UUID accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> ApiException.notFound("账号不存在"));
    }

    private void assertNotSelf(UserAccount account, String message) {
        if (account.getId().equals(currentUser.principal().accountId())) {
            throw ApiException.badRequest("SELF_OPERATION_DENIED", message);
        }
    }

    private AccountView view(UserAccount account) {
        Person person = personRepository.findById(account.getPersonId()).orElse(null);
        List<RoleAssignment> assignments = assignmentRepository.findByPersonIdAndRevokedAtIsNull(account.getPersonId());
        List<String> roles = new ArrayList<>();
        for (RoleAssignment assignment : assignments) {
            roleRepository.findById(assignment.getRoleId()).ifPresent(role -> roles.add(role.getCode()));
        }
        return new AccountView(
                account.getId(),
                account.getPersonId(),
                account.getUsername(),
                person == null ? account.getUsername() : person.getName(),
                person == null ? null : person.getStudentNo(),
                account.getStatus(),
                account.getFailedAttempts(),
                account.getLockedUntil(),
                account.getLastLoginAt(),
                account.isMustChangePassword(),
                roles);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record CreateAccountRequest(
            UUID personId,
            String studentNo,
            String name,
            String username,
            String password,
            List<RoleBinding> roles,
            String reason) {
    }

    public record RoleBinding(String roleCode, String scopeType, UUID scopeId) {
    }

    public record AccountView(
            UUID accountId,
            UUID personId,
            String username,
            String displayName,
            String studentNo,
            String status,
            int failedAttempts,
            Instant lockedUntil,
            Instant lastLoginAt,
            boolean mustChangePassword,
            List<String> roles) {
    }
}