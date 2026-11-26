package cn.edu.scuec.icpc.lab.application;

import cn.edu.scuec.icpc.lab.common.BaseEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "membership_application")
public class MembershipApplication extends BaseEntity {

    @Column(nullable = false)
    private UUID applicantPersonId;

    @Column(nullable = false, length = 16)
    private String type;

    @Column(nullable = false, length = 16)
    private String status = ApplicationStatus.DRAFT;

    @Column(nullable = false)
    private int currentVersion = 1;

    @Column(length = 16)
    private String openType;

    @Column(nullable = false, length = 16)
    private String channel = "GENERAL";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String form;

    private OffsetDateTime submittedAt;
    private OffsetDateTime decidedAt;
    private OffsetDateTime effectiveAt;
    private OffsetDateTime effectedAt;

    private UUID createdBy;
    private UUID updatedBy;

    public UUID getApplicantPersonId() { return applicantPersonId; }
    public void setApplicantPersonId(UUID applicantPersonId) { this.applicantPersonId = applicantPersonId; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getCurrentVersion() { return currentVersion; }
    public void setCurrentVersion(int currentVersion) { this.currentVersion = currentVersion; }
    public String getOpenType() { return openType; }
    public void setOpenType(String openType) { this.openType = openType; }
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    public String getForm() { return form; }
    public void setForm(String form) { this.form = form; }
    public OffsetDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(OffsetDateTime submittedAt) { this.submittedAt = submittedAt; }
    public OffsetDateTime getDecidedAt() { return decidedAt; }
    public void setDecidedAt(OffsetDateTime decidedAt) { this.decidedAt = decidedAt; }
    public OffsetDateTime getEffectiveAt() { return effectiveAt; }
    public void setEffectiveAt(OffsetDateTime effectiveAt) { this.effectiveAt = effectiveAt; }
    public OffsetDateTime getEffectedAt() { return effectedAt; }
    public void setEffectedAt(OffsetDateTime effectedAt) { this.effectedAt = effectedAt; }
    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }
    public UUID getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(UUID updatedBy) { this.updatedBy = updatedBy; }
}