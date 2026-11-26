package cn.edu.scuec.icpc.lab.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * 审计日志：只追加，不提供更新/删除入口（DATA-05 / I-21 / DOM-27）。
 * PostgreSQL 侧另有触发器阻断 UPDATE/DELETE/TRUNCATE。
 */
@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    private UUID id;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    private UUID actorAccountId;
    private UUID actorPersonId;
    private String actorName;
    private String action;
    private String objectType;
    private String objectId;

    @Column(name = "before_snapshot", length = 4000)
    private String beforeSnapshot;

    @Column(name = "after_snapshot", length = 4000)
    private String afterSnapshot;

    private String reason;
    private String requestId;
    private String ip;
    private String userAgent;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    public UUID getActorAccountId() {
        return actorAccountId;
    }

    public void setActorAccountId(UUID actorAccountId) {
        this.actorAccountId = actorAccountId;
    }

    public UUID getActorPersonId() {
        return actorPersonId;
    }

    public void setActorPersonId(UUID actorPersonId) {
        this.actorPersonId = actorPersonId;
    }

    public String getActorName() {
        return actorName;
    }

    public void setActorName(String actorName) {
        this.actorName = actorName;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getObjectType() {
        return objectType;
    }

    public void setObjectType(String objectType) {
        this.objectType = objectType;
    }

    public String getObjectId() {
        return objectId;
    }

    public void setObjectId(String objectId) {
        this.objectId = objectId;
    }

    public String getBeforeSnapshot() {
        return beforeSnapshot;
    }

    public void setBeforeSnapshot(String beforeSnapshot) {
        this.beforeSnapshot = beforeSnapshot;
    }

    public String getAfterSnapshot() {
        return afterSnapshot;
    }

    public void setAfterSnapshot(String afterSnapshot) {
        this.afterSnapshot = afterSnapshot;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }
}