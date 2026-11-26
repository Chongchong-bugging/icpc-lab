package cn.edu.scuec.icpc.lab.approval;

import cn.edu.scuec.icpc.lab.audit.AuditService;
import cn.edu.scuec.icpc.lab.common.ApiException;
import cn.edu.scuec.icpc.lab.security.AccountPrincipal;
import cn.edu.scuec.icpc.lab.security.CurrentUser;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 通用审批（FR-29）：N 级、待办、结果留痕、退回重提。节点只追加。 */
@Service
public class ApprovalService {

    public static final String MEMBERSHIP_APPLICATION = "MEMBERSHIP_APPLICATION";

    private final ApprovalInstanceRepository instanceRepository;
    private final ApprovalNodeRepository nodeRepository;
    private final CurrentUser currentUser;
    private final AuditService auditService;

    public ApprovalService(ApprovalInstanceRepository instanceRepository, ApprovalNodeRepository nodeRepository,
                           CurrentUser currentUser, AuditService auditService) {
        this.instanceRepository = instanceRepository;
        this.nodeRepository = nodeRepository;
        this.currentUser = currentUser;
        this.auditService = auditService;
    }

    @Transactional
    public ApprovalInstance open(String bizType, UUID bizId, String title, int totalLevels) {
        ApprovalInstance instance = new ApprovalInstance();
        instance.setBizType(bizType);
        instance.setBizId(bizId);
        instance.setTitle(title);
        instance.setStatus(ApprovalInstance.PENDING);
        instance.setCurrentLevel(1);
        instance.setTotalLevels(Math.max(1, totalLevels));
        instance.setCreatedBy(currentUser.principal().personId());
        return instanceRepository.save(instance);
    }

    public ApprovalInstance requirePending(String bizType, UUID bizId) {
        return instanceRepository.findFirstByBizTypeAndBizIdAndStatusOrderByCreatedAtDesc(
                        bizType, bizId, ApprovalInstance.PENDING)
                .orElseThrow(() -> ApiException.conflict("APPROVAL_NOT_PENDING", "没有待处理的审批实例"));
    }

    /**
     * 记录一次审批动作。通过且未达末级时推进到下一级；驳回/退回终结当前实例。
     */
    @Transactional
    public ApprovalInstance decide(ApprovalInstance instance, UUID actorPersonId, String decision, String comment) {
        if (!ApprovalInstance.PENDING.equals(instance.getStatus())) {
            throw ApiException.conflict("APPROVAL_CLOSED", "该审批已结束");
        }
        if (actorPersonId.equals(instance.getCreatedBy())) {
            throw ApiException.forbidden("SELF_APPROVAL_DENIED", "不得审批本人提交的事项");
        }
        ApprovalNode node = new ApprovalNode();
        node.setInstanceId(instance.getId());
        node.setLevel(instance.getCurrentLevel());
        node.setAssigneePersonId(actorPersonId);
        node.setDecision(decision);
        node.setComment(comment);
        node.setDecidedAt(OffsetDateTime.now());
        nodeRepository.save(node);

        if (ApprovalNode.APPROVE.equals(decision)) {
            if (instance.getCurrentLevel() >= instance.getTotalLevels()) {
                instance.setStatus(ApprovalInstance.APPROVED);
            } else {
                instance.setCurrentLevel(instance.getCurrentLevel() + 1);
            }
        } else if (ApprovalNode.REJECT.equals(decision)) {
            instance.setStatus(ApprovalInstance.REJECTED);
        } else if (ApprovalNode.RETURN.equals(decision)) {
            instance.setStatus(ApprovalInstance.REJECTED);
        } else {
            throw ApiException.badRequest("INVALID_DECISION", "审批动作只能是 APPROVE/REJECT/RETURN");
        }
        instanceRepository.save(instance);
        auditService.record("approval." + decision.toLowerCase(), "approval_instance", instance.getId(),
                Map.of("level", instance.getCurrentLevel()),
                Map.of("status", instance.getStatus(), "bizType", instance.getBizType()), comment);
        return instance;
    }

    @Transactional
    public void cancel(ApprovalInstance instance) {
        if (ApprovalInstance.PENDING.equals(instance.getStatus())) {
            instance.setStatus(ApprovalInstance.CANCELLED);
            instanceRepository.save(instance);
        }
    }

    public List<TodoView> todo() {
        AccountPrincipal principal = currentUser.principal();
        return instanceRepository.findAll().stream()
                .filter(i -> ApprovalInstance.PENDING.equals(i.getStatus()))
                .filter(i -> principal.hasPermission(permissionFor(i.getBizType())))
                .map(TodoView::of)
                .toList();
    }

    public ApprovalDetail detail(UUID instanceId) {
        ApprovalInstance instance = instanceRepository.findById(instanceId)
                .orElseThrow(() -> ApiException.notFound("审批实例不存在"));
        List<ApprovalNode> nodes = nodeRepository.findByInstanceIdOrderByLevelAscCreatedAtAsc(instanceId);
        return new ApprovalDetail(instance.getId(), instance.getBizType(), instance.getBizId(), instance.getTitle(),
                instance.getStatus(), instance.getCurrentLevel(), instance.getTotalLevels(),
                nodes.stream().map(NodeView::of).toList());
    }

    private static String permissionFor(String bizType) {
        if (MEMBERSHIP_APPLICATION.equals(bizType)) {
            return "application:review";
        }
        if ("TRAINING_PLAN".equals(bizType)) {
            return "plan:approve";
        }
        return "approval:act";
    }

    public record TodoView(UUID id, String bizType, UUID bizId, String title, int currentLevel, int totalLevels,
                           java.time.Instant createdAt) {
        static TodoView of(ApprovalInstance i) {
            return new TodoView(i.getId(), i.getBizType(), i.getBizId(), i.getTitle(),
                    i.getCurrentLevel(), i.getTotalLevels(), i.getCreatedAt());
        }
    }

    public record NodeView(UUID id, int level, UUID assigneePersonId, String decision, String comment,
                           OffsetDateTime decidedAt) {
        static NodeView of(ApprovalNode n) {
            return new NodeView(n.getId(), n.getLevel(), n.getAssigneePersonId(), n.getDecision(),
                    n.getComment(), n.getDecidedAt());
        }
    }

    public record ApprovalDetail(UUID id, String bizType, UUID bizId, String title, String status,
                                 int currentLevel, int totalLevels, List<NodeView> nodes) {
    }
}