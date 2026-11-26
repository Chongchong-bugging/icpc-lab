package cn.edu.scuec.icpc.lab.member;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/** 身份变更历史，只追加（DOM-15 / DATA-05）。 */
@Entity
@Table(name = "identity_history")
public class IdentityHistory {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID personId;

    @Column(nullable = false, length = 16)
    private String dimension;

    @Column(length = 64)
    private String fromValue;

    @Column(nullable = false, length = 64)
    private String toValue;

    @Column(length = 500)
    private String reason;

    private UUID changedBy;

    @Column(nullable = false)
    private OffsetDateTime changedAt = OffsetDateTime.now();

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (changedAt == null) changedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getPersonId() { return personId; }
    public void setPersonId(UUID personId) { this.personId = personId; }
    public String getDimension() { return dimension; }
    public void setDimension(String dimension) { this.dimension = dimension; }
    public String getFromValue() { return fromValue; }
    public void setFromValue(String fromValue) { this.fromValue = fromValue; }
    public String getToValue() { return toValue; }
    public void setToValue(String toValue) { this.toValue = toValue; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public UUID getChangedBy() { return changedBy; }
    public void setChangedBy(UUID changedBy) { this.changedBy = changedBy; }
    public OffsetDateTime getChangedAt() { return changedAt; }
    public void setChangedAt(OffsetDateTime changedAt) { this.changedAt = changedAt; }
}