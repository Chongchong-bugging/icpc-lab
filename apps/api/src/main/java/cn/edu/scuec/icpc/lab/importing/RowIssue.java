package cn.edu.scuec.icpc.lab.importing;

/**
 * 单行导入错误（DOM-24 错误行回执）。
 * rowIndex 是数据行下标（0 起，不含表头）；对外行号 = rowIndex + 2（表头为第 1 行）。
 */
public record RowIssue(int rowIndex, String code, String column, String message, String rawValue) {
}