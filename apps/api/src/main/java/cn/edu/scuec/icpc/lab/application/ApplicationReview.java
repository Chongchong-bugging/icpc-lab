package cn.edu.scuec.icpc.lab.application;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/** 审核轨迹，只追加（DOM-07）。每行记录审核人/时间/决定/意见/依据版本。 */
@Entity
@Table(name = "application_review")
public class ApplicationReview {

    public static final String APPROVE = "APPROVE";
    public static final String REJECT = "REJECT";
    public static final String RETURN = "RETURN";

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID applicationId;

    @Column(nullable = false)
    private int versionNo;

    @Column(nullable = false)
    private UUID reviewerPersonId;

    @Column(nullable = false, length = 16)
    private String decision;

    @Column(length = 1000)
    private String comment;

    @Column(nullable = false)
    private OffsetDateTime decidedAt = OffsetDateTime.now();

    @Column(nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (decidedAt == null) decidedAt = OffsetDateTime.now();
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getApplicationId() { return applicationId; }
    public void setApplicationId(UUID applicationId) { this.applicationId = applicationId; }
    public int getVersionNo() { return versionNo; }
    public void setVersionNo(int versionNo) { this.versionNo = versionNo; }
    public UUID getReviewerPersonId() { return reviewerPersonId; }
    public void setReviewerPersonId(UUID reviewerPersonId) { this.reviewerPersonId = reviewerPersonId; }
    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public OffsetDateTime getDecidedAt() { return decidedAt; }
    public void setDecidedAt(OffsetDateTime decidedAt) { this.decidedAt = decidedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}