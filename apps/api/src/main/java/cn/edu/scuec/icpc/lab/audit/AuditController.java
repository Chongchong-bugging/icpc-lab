package cn.edu.scuec.icpc.lab.audit;

import cn.edu.scuec.icpc.lab.common.PageResponse;
import cn.edu.scuec.icpc.lab.security.ScopeGuard;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 审计只读查询（权限矩阵：audit:read）。不存在任何写入/修改端点。
 */
@RestController
@RequestMapping("/api/v1/audit-logs")
public class AuditController {

    private final AuditService auditService;
    private final ScopeGuard scopeGuard;

    public AuditController(AuditService auditService, ScopeGuard scopeGuard) {
        this.auditService = auditService;
        this.scopeGuard = scopeGuard;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('audit:read')")
    public PageResponse<AuditService.AuditView> list(
            @RequestParam(required = false) String objectType,
            @RequestParam(required = false) String objectId,
            @RequestParam(required = false) UUID actorPersonId,
            @RequestParam(required = false) String action,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        scopeGuard.requireAnyScope("audit:read");
        return auditService.query(objectType, objectId, actorPersonId, action, page, size);
    }
}