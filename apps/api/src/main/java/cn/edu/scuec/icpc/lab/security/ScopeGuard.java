package cn.edu.scuec.icpc.lab.security;

import cn.edu.scuec.icpc.lab.common.ApiException;
import cn.edu.scuec.icpc.lab.identity.Person;
import cn.edu.scuec.icpc.lab.identity.PersonRepository;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * 数据范围守卫（TECH-11 / 权限矩阵 §2、§5）。
 * 越权统一 403，不泄露对象是否存在；SELF 只允许本人，DEPARTMENT 允许本人部门，GLOBAL 放行。
 */
@Component
public class ScopeGuard {

    private final CurrentUser currentUser;
    private final PersonRepository personRepository;

    public ScopeGuard(CurrentUser currentUser, PersonRepository personRepository) {
        this.currentUser = currentUser;
        this.personRepository = personRepository;
    }

    public boolean has(String permission) {
        return currentUser.principal().hasPermission(permission);
    }

    /** 管理员类操作只接受 GLOBAL 范围的授予（如 account/role/rule）。 */
    public void requireGlobal(String permission) {
        AccountPrincipal principal = currentUser.principal();
        boolean allowed = principal.grants().stream()
                .anyMatch(grant -> grant.permission().equals(permission) && grant.scopeType() == ScopeType.GLOBAL);
        if (!allowed) {
            throw ApiException.forbidden("FORBIDDEN", "无该操作的全局权限");
        }
    }

    /** 至少有一个范围内的授予（SELF/TEAM/DEPARTMENT/GLOBAL）。 */
    public void requireAnyScope(String permission) {
        if (!has(permission)) {
            throw ApiException.forbidden("FORBIDDEN", "缺少权限：" + permission);
        }
    }

    public boolean canAccessPerson(UUID targetPersonId, String permission) {
        AccountPrincipal principal = currentUser.principal();
        List<ScopeGrant> grants = principal.grants().stream()
                .filter(grant -> grant.permission().equals(permission))
                .toList();
        if (grants.isEmpty()) {
            return false;
        }
        if (grants.stream().anyMatch(grant -> grant.scopeType() == ScopeType.GLOBAL)) {
            return true;
        }
        if (!Objects.equals(principal.personId(), targetPersonId)
                && grants.stream().noneMatch(grant -> grant.scopeType() == ScopeType.DEPARTMENT)) {
            return false;
        }
        Person target = personRepository.findById(targetPersonId).orElse(null);
        if (target == null) {
            return false;
        }
        if (Objects.equals(principal.personId(), targetPersonId)) {
            return true;
        }
        return grants.stream()
                .filter(grant -> grant.scopeType() == ScopeType.DEPARTMENT)
                .anyMatch(grant -> grant.scopeId() != null
                        && grant.scopeId().equals(target.getDepartmentId()));
    }

    public void assertCanAccessPerson(UUID targetPersonId, String permission) {
        if (!canAccessPerson(targetPersonId, permission)) {
            throw ApiException.forbidden("FORBIDDEN", "超出数据范围");
        }
    }

    /** 禁止自批：审批人不得等于申请人/发起人（I-07）。 */
    public void assertNotSelf(UUID subjectPersonId) {
        if (Objects.equals(currentUser.principal().personId(), subjectPersonId)) {
            throw ApiException.forbidden("SELF_APPROVAL_DENIED", "不得审批本人提交的事项");
        }
    }
}