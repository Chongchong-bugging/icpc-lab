package cn.edu.scuec.icpc.lab.member;

import cn.edu.scuec.icpc.lab.common.BaseEntity;
import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/** 学期注册与考核（FR-04）。考核状态三态：PENDING/QUALIFIED/OBSERVING。 */
@Entity
@Table(name = "semester_registration")
public class SemesterRegistration extends BaseEntity {

    public static final String REGISTERED = "REGISTERED";
    public static final String PENDING = "PENDING";
    public static final String DECLINED = "DECLINED";

    public static final String ASSESS_PENDING = "PENDING";
    public static final String QUALIFIED = "QUALIFIED";
    public static final String OBSERVING = "OBSERVING";

    @Column(nullable = false)
    private UUID personId;

    @Column(nullable = false)
    private UUID semesterId;

    @Column(nullable = false, length = 16)
    private String status = REGISTERED;

    @Column(nullable = false, length = 16)
    private String assessment = ASSESS_PENDING;

    @Column(length = 500)
    private String note;

    private UUID assessedBy;
    private OffsetDateTime assessedAt;

    public UUID getPersonId() { return personId; }
    public void setPersonId(UUID personId) { this.personId = personId; }
    public UUID getSemesterId() { return semesterId; }
    public void setSemesterId(UUID semesterId) { this.semesterId = semesterId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getAssessment() { return assessment; }
    public void setAssessment(String assessment) { this.assessment = assessment; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public UUID getAssessedBy() { return assessedBy; }
    public void setAssessedBy(UUID assessedBy) { this.assessedBy = assessedBy; }
    public OffsetDateTime getAssessedAt() { return assessedAt; }
    public void setAssessedAt(OffsetDateTime assessedAt) { this.assessedAt = assessedAt; }
}