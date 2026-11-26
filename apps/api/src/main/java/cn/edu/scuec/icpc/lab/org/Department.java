package cn.edu.scuec.icpc.lab.org;

import cn.edu.scuec.icpc.lab.common.BaseEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "department")
public class Department extends BaseEntity {

    @Column(nullable = false, length = 64)
    private String code;

    @Column(nullable = false, length = 128)
    private String name;

    private java.util.UUID parentId;

    @Column(nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean active = true;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public java.util.UUID getParentId() { return parentId; }
    public void setParentId(java.util.UUID parentId) { this.parentId = parentId; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}