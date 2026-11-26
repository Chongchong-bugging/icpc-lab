package cn.edu.scuec.icpc.lab.importing;

import java.util.List;
import java.util.Map;

/**
 * 导入处理器：负责某一业务类型的整表校验与落库（DOM-24）。
 * validate 与 apply 使用同一列定义，保证「预检 → 确认」两阶段一致。
 */
public interface ImportHandler {

    String kind();

    List<String> requiredColumns();

    /** 校验整表（可做跨行查重），返回全部错误行。 */
    List<RowIssue> validate(List<Map<String, String>> rows);

    /** 落库；rows 保证全部通过校验，且在同一事务内执行。 */
    void apply(List<Map<String, String>> rows);
}