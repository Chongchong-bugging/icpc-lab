package cn.edu.scuec.icpc.lab.category;

import cn.edu.scuec.icpc.lab.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * 知识点前置关系（FR-39 的图能力；DOM-20 保存时拒环）。
 */
@Entity
@Table(name = "category_relation")
public class CategoryRelation extends BaseEntity {

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(name = "prerequisite_id", nullable = false)
    private UUID prerequisiteId;

    @Column(length = 500)
    private String note;

    public UUID getCategoryId() { return categoryId; }
    public void setCategoryId(UUID categoryId) { this.categoryId = categoryId; }
    public UUID getPrerequisiteId() { return prerequisiteId; }
    public void setPrerequisiteId(UUID prerequisiteId) { this.prerequisiteId = prerequisiteId; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}