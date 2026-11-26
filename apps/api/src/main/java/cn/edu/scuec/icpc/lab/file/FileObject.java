package cn.edu.scuec.icpc.lab.file;

import cn.edu.scuec.icpc.lab.common.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * 附件元数据。文件本体落盘，数据库只存元数据（TECH-13）。
 * 业务记录删除时附件最多归档，不物理删除（DOM-11）。
 */
@Entity
@Table(name = "file_object")
public class FileObject extends BaseEntity {

    public static final String PRIVATE = "PRIVATE";
    public static final String SCOPED = "SCOPED";
    public static final String INTERNAL = "INTERNAL";

    private UUID ownerPersonId;
    private String originalName;
    private String storedName;
    private String contentType;
    private long sizeBytes;
    private String sha256;
    private String visibility = SCOPED;
    private String objectType;
    private UUID objectId;
    private Instant archivedAt;

    public UUID getOwnerPersonId() {
        return ownerPersonId;
    }

    public void setOwnerPersonId(UUID ownerPersonId) {
        this.ownerPersonId = ownerPersonId;
    }

    public String getOriginalName() {
        return originalName;
    }

    public void setOriginalName(String originalName) {
        this.originalName = originalName;
    }

    public String getStoredName() {
        return storedName;
    }

    public void setStoredName(String storedName) {
        this.storedName = storedName;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public String getSha256() {
        return sha256;
    }

    public void setSha256(String sha256) {
        this.sha256 = sha256;
    }

    public String getVisibility() {
        return visibility;
    }

    public void setVisibility(String visibility) {
        this.visibility = visibility;
    }

    public String getObjectType() {
        return objectType;
    }

    public void setObjectType(String objectType) {
        this.objectType = objectType;
    }

    public UUID getObjectId() {
        return objectId;
    }

    public void setObjectId(UUID objectId) {
        this.objectId = objectId;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }

    public void setArchivedAt(Instant archivedAt) {
        this.archivedAt = archivedAt;
    }
}