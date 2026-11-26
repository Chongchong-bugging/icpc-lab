package cn.edu.scuec.icpc.lab.problem;

import cn.edu.scuec.icpc.lab.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * 题目（DOM-19 / DOM-22 / DOM-23）：source+title 规范化唯一，重复导入必须显式提示。
 */
@Entity
@Table(name = "problem")
public class Problem extends BaseEntity {

    @Column(length = 64)
    private String code;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(nullable = false, length = 64)
    private String source = "OTHER";

    @Column(name = "source_key", nullable = false, length = 64)
    private String sourceKey;

    @Column(name = "title_key", nullable = false, length = 320)
    private String titleKey;

    @Column(length = 500)
    private String link;

    @Column(length = 32)
    private String difficulty;

    @Column(length = 4000)
    private String statement;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getSourceKey() { return sourceKey; }
    public void setSourceKey(String sourceKey) { this.sourceKey = sourceKey; }
    public String getTitleKey() { return titleKey; }
    public void setTitleKey(String titleKey) { this.titleKey = titleKey; }
    public String getLink() { return link; }
    public void setLink(String link) { this.link = link; }
    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }
    public String getStatement() { return statement; }
    public void setStatement(String statement) { this.statement = statement; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }
    public UUID getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(UUID updatedBy) { this.updatedBy = updatedBy; }
}