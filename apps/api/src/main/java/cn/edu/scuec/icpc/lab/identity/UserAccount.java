package cn.edu.scuec.icpc.lab.identity;

import cn.edu.scuec.icpc.lab.common.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * 登录账号。密码只存 Argon2id 哈希；token_version 用于停用/改密后即时失效（TECH-12）。
 */
@Entity
@Table(name = "user_account")
public class UserAccount extends BaseEntity {

    public static final String ACTIVE = "ACTIVE";
    public static final String DISABLED = "DISABLED";

    private UUID personId;
    private String username;
    private String passwordHash;
    private String status = ACTIVE;
    private int failedAttempts;
    private Instant lockedUntil;
    private long tokenVersion = 1;
    private boolean mustChangePassword;
    private Instant lastLoginAt;

    public UUID getPersonId() {
        return personId;
    }

    public void setPersonId(UUID personId) {
        this.personId = personId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getFailedAttempts() {
        return failedAttempts;
    }

    public void setFailedAttempts(int failedAttempts) {
        this.failedAttempts = failedAttempts;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public void setLockedUntil(Instant lockedUntil) {
        this.lockedUntil = lockedUntil;
    }

    public long getTokenVersion() {
        return tokenVersion;
    }

    public void setTokenVersion(long tokenVersion) {
        this.tokenVersion = tokenVersion;
    }

    public void bumpTokenVersion() {
        this.tokenVersion += 1;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    public void setMustChangePassword(boolean mustChangePassword) {
        this.mustChangePassword = mustChangePassword;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(Instant lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    public boolean isActive() {
        return ACTIVE.equals(status);
    }
}