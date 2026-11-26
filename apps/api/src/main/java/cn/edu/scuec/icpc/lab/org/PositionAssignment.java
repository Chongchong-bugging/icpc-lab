package cn.edu.scuec.icpc.lab.org;

import cn.edu.scuec.icpc.lab.common.BaseEntity;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "position_assignment")
public class PositionAssignment extends BaseEntity {

    public static final String ACTIVE = "ACTIVE";
    public static final String ENDED = "ENDED";

    @Column(nullable = false)
    private UUID personId;

    @Column(nullable = false)
    private UUID positionId;

    private UUID departmentId;
    private UUID termId;
    private UUID roleAssignmentId;

    @Column(nullable = false)
    private LocalDate startDate;

    private LocalDate endDate;

    @Column(nullable = false, length = 16)
    private String status = ACTIVE;

    @Column(length = 128)
    private String activeKey;

    @Column(length = 500)
    private String reason;

    private UUID grantedBy;
    private UUID endedBy;
    private OffsetDateTime endedAt;

    public UUID getPersonId() { return personId; }
    public void setPersonId(UUID personId) { this.personId = personId; }
    public UUID getPositionId() { return positionId; }
    public void setPositionId(UUID positionId) { this.positionId = positionId; }
    public UUID getDepartmentId() { return departmentId; }
    public void setDepartmentId(UUID departmentId) { this.departmentId = departmentId; }
    public UUID getTermId() { return termId; }
    public void setTermId(UUID termId) { this.termId = termId; }
    public UUID getRoleAssignmentId() { return roleAssignmentId; }
    public void setRoleAssignmentId(UUID roleAssignmentId) { this.roleAssignmentId = roleAssignmentId; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getActiveKey() { return activeKey; }
    public void setActiveKey(String activeKey) { this.activeKey = activeKey; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public UUID getGrantedBy() { return grantedBy; }
    public void setGrantedBy(UUID grantedBy) { this.grantedBy = grantedBy; }
    public UUID getEndedBy() { return endedBy; }
    public void setEndedBy(UUID endedBy) { this.endedBy = endedBy; }
    public OffsetDateTime getEndedAt() { return endedAt; }
    public void setEndedAt(OffsetDateTime endedAt) { this.endedAt = endedAt; }
}