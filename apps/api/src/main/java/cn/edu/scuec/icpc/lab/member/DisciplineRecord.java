package cn.edu.scuec.icpc.lab.member;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/** 奖惩记录，只追加（FR-05 / DATA-05）。 */
@Entity
@Table(name = "discipline_record")
public class DisciplineRecord {

    public static final String REWARD = "REWARD";
    public static final String PUNISH = "PUNISH";

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID personId;

    @Column(nullable = false, length = 16)
    private String kind;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 2000)
    private String detail;

    private UUID decidedBy;

    @Column(nullable = false)
    private OffsetDateTime decidedAt = OffsetDateTime.now();

    @Column(nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getPersonId() { return personId; }
    public void setPersonId(UUID personId) { this.personId = personId; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
    public UUID getDecidedBy() { return decidedBy; }
    public void setDecidedBy(UUID decidedBy) { this.decidedBy = decidedBy; }
    public OffsetDateTime getDecidedAt() { return decidedAt; }
    public void setDecidedAt(OffsetDateTime decidedAt) { this.decidedAt = decidedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}