package cn.edu.scuec.icpc.lab.trainingplan;

import cn.edu.scuec.icpc.lab.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * 训练计划（WP-P0-14 / FR-38 / FR-40）。状态机由服务层维护，内容以 JSON 文本保存。
 */
@Entity
@Table(name = "training_plan")
public class TrainingPlan extends BaseEntity {

    public static final String SEMESTER = "SEMESTER";
    public static final String WINTER = "WINTER";
    public static final String SUMMER = "SUMMER";

    public static final String DRAFT = "DRAFT";
    public static final String SUBMITTED = "SUBMITTED";
    public static final String APPROVED = "APPROVED";
    public static final String PUBLISHED = "PUBLISHED";
    public static final String ARCHIVED = "ARCHIVED";

    @Column(name = "term_id")
    private UUID termId;

    @Column(name = "semester_id")
    private UUID semesterId;

    @Column(nullable = false, length = 16)
    private String kind = SEMESTER;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 16)
    private String status = DRAFT;

    @Column(name = "current_version", nullable = false)
    private int currentVersion = 1;

    @Column(name = "content")
    private String content;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "published_by")
    private UUID publishedBy;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;

    public UUID getTermId() {
        return termId;
    }

    public void setTermId(UUID termId) {
        this.termId = termId;
    }

    public UUID getSemesterId() {
        return semesterId;
    }

    public void setSemesterId(UUID semesterId) {
        this.semesterId = semesterId;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getCurrentVersion() {
        return currentVersion;
    }

    public void setCurrentVersion(int currentVersion) {
        this.currentVersion = currentVersion;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    public UUID getPublishedBy() {
        return publishedBy;
    }

    public void setPublishedBy(UUID publishedBy) {
        this.publishedBy = publishedBy;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
    }

    public UUID getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(UUID updatedBy) {
        this.updatedBy = updatedBy;
    }
}