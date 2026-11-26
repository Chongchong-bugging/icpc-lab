package cn.edu.scuec.icpc.lab.importing;

import cn.edu.scuec.icpc.lab.common.ApiException;

import java.util.ArrayList;
import java.util.List;

/**
 * RFC 4180 CSV 解析（DOM-24 / TECH-11：不引第三方库）。
 * 支持双引号包裹、引号转义（""）、CRLF/LF、UTF-8 BOM。
 */
public final class CsvParser {

    private CsvParser() {
    }

    public static List<List<String>> parse(String content) {
        String text = content.startsWith("\uFEFF") ? content.substring(1) : content;
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        boolean rowHasContent = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (quoted) {
                if (ch == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        cell.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    cell.append(ch);
                }
                continue;
            }
            switch (ch) {
                case '"' -> {
                    if (!cell.isEmpty()) {
                        throw ApiException.badRequest("IMPORT_CSV_MALFORMED", "CSV 引号位置非法（第 " + (rows.size() + 1) + " 行）");
                    }
                    quoted = true;
                    rowHasContent = true;
                }
                case ',' -> {
                    row.add(cell.toString());
                    cell.setLength(0);
                    rowHasContent = true;
                }
                case '\r' -> {
                    // 由 \n 统一处理行尾；单独的 \r 视为行尾。
                    if (i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                        i++;
                    }
                    row.add(cell.toString());
                    cell.setLength(0);
                    rows.add(row);
                    row = new ArrayList<>();
                    rowHasContent = false;
                }
                case '\n' -> {
                    row.add(cell.toString());
                    cell.setLength(0);
                    rows.add(row);
                    row = new ArrayList<>();
                    rowHasContent = false;
                }
                default -> {
                    cell.append(ch);
                    rowHasContent = true;
                }
            }
        }
        if (quoted) {
            throw ApiException.badRequest("IMPORT_CSV_MALFORMED", "CSV 引号未闭合");
        }
        if (rowHasContent || cell.length() > 0 || !row.isEmpty()) {
            row.add(cell.toString());
            rows.add(row);
        }
        return rows;
    }
}