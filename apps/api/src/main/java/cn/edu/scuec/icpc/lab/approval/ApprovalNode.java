package cn.edu.scuec.icpc.lab.approval;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/** 审批节点，只追加（FR-29）。 */
@Entity
@Table(name = "approval_node")
public class ApprovalNode {

    public static final String PENDING = "PENDING";
    public static final String APPROVE = "APPROVE";
    public static final String REJECT = "REJECT";
    public static final String RETURN = "RETURN";

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID instanceId;

    @Column(nullable = false)
    private int level;

    @Column(nullable = false)
    private UUID assigneePersonId;

    @Column(nullable = false, length = 16)
    private String decision = PENDING;

    @Column(length = 1000)
    private String comment;

    private OffsetDateTime decidedAt;

    @Column(nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getInstanceId() { return instanceId; }
    public void setInstanceId(UUID instanceId) { this.instanceId = instanceId; }
    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }
    public UUID getAssigneePersonId() { return assigneePersonId; }
    public void setAssigneePersonId(UUID assigneePersonId) { this.assigneePersonId = assigneePersonId; }
    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public OffsetDateTime getDecidedAt() { return decidedAt; }
    public void setDecidedAt(OffsetDateTime decidedAt) { this.decidedAt = decidedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}