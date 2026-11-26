package cn.edu.scuec.icpc.lab.audit;

import cn.edu.scuec.icpc.lab.common.PageResponse;
import cn.edu.scuec.icpc.lab.security.AccountPrincipal;
import cn.edu.scuec.icpc.lab.security.CurrentUser;
import cn.edu.scuec.icpc.lab.security.ScopeGuard;
import cn.edu.scuec.icpc.lab.security.ScopeType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 审计写入/查询（WP-P0-05 / DOM-27 / DATA-05）。
 * 只提供新增与查询；生产库另有触发器拒绝 UPDATE/DELETE/TRUNCATE（I-21）。
 */
@Service
public class AuditService {

    private final AuditLogRepository repository;
    private final ObjectMapper objectMapper;
    private final CurrentUser currentUser;

    public AuditService(AuditLogRepository repository, ObjectMapper objectMapper, CurrentUser currentUser) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.currentUser = currentUser;
    }

    /**
     * 在业务事务内写审计；即使调用方事务回滚，审计也应保留失败痕迹，
     * 因此使用独立事务（REQUIRES_NEW）。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String action, String objectType, Object objectId, Object before, Object after, String reason) {
        AccountPrincipal actor = currentUser.principalOrNull();
        AuditLog log = new AuditLog();
        log.setId(UUID.randomUUID());
        log.setOccurredAt(Instant.now());
        if (actor != null) {
            log.setActorAccountId(actor.accountId());
            log.setActorPersonId(actor.personId());
            log.setActorName(actor.displayName());
        }
        log.setAction(action);
        log.setObjectType(objectType);
        log.setObjectId(objectId == null ? null : objectId.toString());
        log.setBeforeSnapshot(toJson(before));
        log.setAfterSnapshot(toJson(after));
        log.setReason(reason);
        log.setRequestId(currentUser.requestId());
        log.setIp(currentUser.ip());
        log.setUserAgent(currentUser.userAgent());
        repository.save(log);
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            // 快照不可序列化时记录占位，不因审计失败阻断业务
            return "{\"unserializable\":\"" + value.getClass().getSimpleName() + "\"}";
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditView> query(String objectType, String objectId, UUID actorPersonId,
                                         String action, int page, int size) {
        AccountPrincipal principal = currentUser.principal();
        boolean global = principal.grants().stream()
                .anyMatch(grant -> grant.permission().equals("audit:read") && grant.scopeType() == ScopeType.GLOBAL);
        Specification<AuditLog> specification = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (objectType != null && !objectType.isBlank()) {
                predicates.add(cb.equal(root.get("objectType"), objectType));
            }
            if (objectId != null && !objectId.isBlank()) {
                predicates.add(cb.equal(root.get("objectId"), objectId));
            }
            if (action != null && !action.isBlank()) {
                predicates.add(cb.equal(root.get("action"), action));
            }
            if (actorPersonId != null) {
                predicates.add(cb.equal(root.get("actorPersonId"), actorPersonId));
            }
            if (!global) {
                // 非全局范围只能看本人相关审计（权限矩阵 §2）
                predicates.add(cb.equal(root.get("actorPersonId"), principal.personId()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        int safeSize = Math.min(Math.max(size, 1), 100);
        Page<AuditLog> result = repository.findAll(specification,
                PageRequest.of(Math.max(page, 0), safeSize, Sort.by(Sort.Direction.DESC, "occurredAt")));
        List<AuditView> items = result.getContent().stream().map(AuditView::of).toList();
        return new PageResponse<>(items, result.getNumber(), result.getSize(), result.getTotalElements());
    }

    public record AuditView(
            UUID id,
            Instant occurredAt,
            UUID actorAccountId,
            UUID actorPersonId,
            String actorName,
            String action,
            String objectType,
            String objectId,
            String beforeSnapshot,
            String afterSnapshot,
            String reason,
            String requestId,
            String ip) {
        static AuditView of(AuditLog log) {
            return new AuditView(
                    log.getId(), log.getOccurredAt(), log.getActorAccountId(), log.getActorPersonId(),
                    log.getActorName(), log.getAction(), log.getObjectType(), log.getObjectId(),
                    log.getBeforeSnapshot(), log.getAfterSnapshot(), log.getReason(),
                    log.getRequestId(), log.getIp());
        }
    }
}