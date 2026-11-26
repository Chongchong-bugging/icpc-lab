package cn.edu.scuec.icpc.lab.member;

import cn.edu.scuec.icpc.lab.common.BaseEntity;
import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/** 会员生命周期与考核状态分维度存储（DOM-02）。active_key 唯一约束保证一人仅一条非终态记录。 */
@Entity
@Table(name = "membership")
public class Membership extends BaseEntity {

    public static final String TEAM_MEMBER = "TEAM_MEMBER";
    public static final String LAB_MEMBER = "LAB_MEMBER";

    public static final String PROBATION = "PROBATION";
    public static final String FULL = "FULL";
    public static final String EXITED = "EXITED";
    public static final String EXPELLED = "EXPELLED";

    @Column(nullable = false)
    private UUID personId;

    @Column(nullable = false, length = 16)
    private String type;

    @Column(nullable = false, length = 16)
    private String lifecycle = PROBATION;

    @Column(nullable = false, length = 16)
    private String assessment = "PENDING";

    private OffsetDateTime joinedAt;
    private OffsetDateTime exitedAt;

    @Column(length = 500)
    private String exitReason;

    @Column(length = 64)
    private String activeKey;

    public UUID getPersonId() { return personId; }
    public void setPersonId(UUID personId) { this.personId = personId; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getLifecycle() { return lifecycle; }
    public void setLifecycle(String lifecycle) { this.lifecycle = lifecycle; }
    public String getAssessment() { return assessment; }
    public void setAssessment(String assessment) { this.assessment = assessment; }
    public OffsetDateTime getJoinedAt() { return joinedAt; }
    public void setJoinedAt(OffsetDateTime joinedAt) { this.joinedAt = joinedAt; }
    public OffsetDateTime getExitedAt() { return exitedAt; }
    public void setExitedAt(OffsetDateTime exitedAt) { this.exitedAt = exitedAt; }
    public String getExitReason() { return exitReason; }
    public void setExitReason(String exitReason) { this.exitReason = exitReason; }
    public String getActiveKey() { return activeKey; }
    public void setActiveKey(String activeKey) { this.activeKey = activeKey; }

    public boolean isTerminal() {
        return EXITED.equals(lifecycle) || EXPELLED.equals(lifecycle);
    }
}