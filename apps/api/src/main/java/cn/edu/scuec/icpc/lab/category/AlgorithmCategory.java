package cn.edu.scuec.icpc.lab.category;

import cn.edu.scuec.icpc.lab.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * 算法目录节点（DOM-18：固定三级，同级名称唯一，支持排序/停用/合并）。
 * nameKey = "父节点 id（根为 root）|规范化名称"，由服务层维护，唯一约束兜底并发重名。
 */
@Entity
@Table(name = "algorithm_category")
public class AlgorithmCategory extends BaseEntity {

    @Column(nullable = false, length = 64)
    private String code;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(name = "name_key", length = 160)
    private String nameKey;

    @Column(name = "short_name", length = 64)
    private String shortName;

    @Column(length = 1000)
    private String description;

    private Integer stage;

    @Column(name = "suggested_difficulty", length = 32)
    private String suggestedDifficulty;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(name = "owner_person_id")
    private UUID ownerPersonId;

    @Column(name = "merged_into_id")
    private UUID mergedIntoId;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @Column(nullable = false)
    private boolean active = true;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getNameKey() { return nameKey; }
    public void setNameKey(String nameKey) { this.nameKey = nameKey; }
    public String getShortName() { return shortName; }
    public void setShortName(String shortName) { this.shortName = shortName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Integer getStage() { return stage; }
    public void setStage(Integer stage) { this.stage = stage; }
    public String getSuggestedDifficulty() { return suggestedDifficulty; }
    public void setSuggestedDifficulty(String suggestedDifficulty) { this.suggestedDifficulty = suggestedDifficulty; }
    public UUID getParentId() { return parentId; }
    public void setParentId(UUID parentId) { this.parentId = parentId; }
    public UUID getOwnerPersonId() { return ownerPersonId; }
    public void setOwnerPersonId(UUID ownerPersonId) { this.ownerPersonId = ownerPersonId; }
    public UUID getMergedIntoId() { return mergedIntoId; }
    public void setMergedIntoId(UUID mergedIntoId) { this.mergedIntoId = mergedIntoId; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}