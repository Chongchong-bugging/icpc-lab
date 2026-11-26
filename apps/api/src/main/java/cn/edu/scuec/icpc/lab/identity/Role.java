package cn.edu.scuec.icpc.lab.identity;

import cn.edu.scuec.icpc.lab.common.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * 角色字典。role_permission 关系用 JDBC 读取，实体只承载角色本身。
 */
@Entity
@Table(name = "role")
public class Role extends BaseEntity {

    private String code;
    private String name;
    private String description;
    private boolean builtin;
    private boolean active = true;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isBuiltin() {
        return builtin;
    }

    public void setBuiltin(boolean builtin) {
        this.builtin = builtin;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}