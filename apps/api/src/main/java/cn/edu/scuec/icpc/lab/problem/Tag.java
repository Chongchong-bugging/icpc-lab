package cn.edu.scuec.icpc.lab.problem;

import cn.edu.scuec.icpc.lab.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * 标签字典（DOM-19）：全局共享，删除以停用代替。
 */
@Entity
@Table(name = "tag")
public class Tag extends BaseEntity {

    @Column(nullable = false, length = 64)
    private String name;

    @Column(nullable = false, length = 16)
    private String type = "TYPE";

    @Column(nullable = false)
    private boolean active = true;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}