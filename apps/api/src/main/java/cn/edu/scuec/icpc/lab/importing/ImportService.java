package cn.edu.scuec.icpc.lab.importing;

import cn.edu.scuec.icpc.lab.audit.AuditService;
import cn.edu.scuec.icpc.lab.common.ApiException;
import cn.edu.scuec.icpc.lab.security.CurrentUser;
import cn.edu.scuec.icpc.lab.security.ScopeGuard;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 通用导入框架（WP-P0-13 / DOM-24 / DATA-11 / NFR-05）。
 * 两阶段：预检返回逐行错误（HTTP 200）；确认在同一事务内落库，任一错误则整批拒绝。
 * 同一文件（kind + sha256）重复提交返回原批次，保证幂等。
 */
@Service
public class ImportService {

    static final long MAX_BYTES = 5L * 1024 * 1024;
    static final int MAX_ROWS = 5000;
    private static final int MAX_ERRORS_IN_RESPONSE = 200;
    private static final Set<String> KINDS = Set.of("MEMBER", "ATTENDANCE", "RESULT");

    private final List<ImportHandler> handlers;
    private final JdbcTemplate jdbc;
    private final ScopeGuard scopeGuard;
    private final CurrentUser currentUser;
    private final AuditService auditService;
    private final ImportBatchWriter batchWriter;

    public ImportService(List<ImportHandler> handlers, JdbcTemplate jdbc, ScopeGuard scopeGuard,
                         CurrentUser currentUser, AuditService auditService, ImportBatchWriter batchWriter) {
        this.handlers = handlers;
        this.jdbc = jdbc;
        this.scopeGuard = scopeGuard;
        this.currentUser = currentUser;
        this.auditService = auditService;
        this.batchWriter = batchWriter;
    }

    @Transactional
    public BatchView preview(String kindRaw, MultipartFile file) {
        scopeGuard.requireGlobal("import:write");
        String kind = normalizeKind(kindRaw);
        ImportHandler handler = handlerFor(kind);
        byte[] bytes = readBytes(file);
        String sha256 = sha256(bytes);

        UUID applied = findBatch(kind, sha256, "APPLIED");
        if (applied != null) {
            return view(applied, true);
        }
        UUID preview = findBatch(kind, sha256, "PREVIEW");
        if (preview != null) {
            return view(preview, true);
        }

        Parsed parsed = parseAndValidate(handler, bytes);
        UUID batchId = UUID.randomUUID();
        jdbc.update("insert into import_batch (id, kind, file_name, file_sha256, status, total_rows, valid_rows,"
                        + " invalid_rows, created_by) values (?, ?, ?, ?, 'PREVIEW', ?, ?, ?, ?)",
                batchId, kind, fileName(file), sha256, parsed.totalRows(), parsed.validRows(),
                parsed.totalRows() - parsed.validRows(), currentUser.principal().personId());
        persistErrors(batchId, parsed);
        auditService.record("import.preview", "import_batch", batchId, null,
                Map.of("kind", kind, "totalRows", parsed.totalRows(), "invalidRows",
                        parsed.totalRows() - parsed.validRows()), null);
        return view(batchId, false);
    }

    @Transactional
    public BatchView apply(UUID batchId, MultipartFile file) {
        scopeGuard.requireGlobal("import:write");
        ImportBatchRecord batch = requireBatch(batchId);
        if ("APPLIED".equals(batch.status())) {
            return view(batchId, true);
        }
        if ("FAILED".equals(batch.status())) {
            throw ApiException.conflict("IMPORT_BATCH_FAILED", "批次已失败，请重新预检");
        }
        ImportHandler handler = handlerFor(batch.kind());
        byte[] bytes = readBytes(file);
        if (!sha256(bytes).equals(batch.fileSha256())) {
            throw ApiException.badRequest("IMPORT_FILE_MISMATCH", "确认文件与预检文件不一致（sha256 不同）");
        }
        Parsed parsed = parseAndValidate(handler, bytes);
        if (!parsed.issues().isEmpty()) {
            batchWriter.markFailed(batchId);
            throw ApiException.badRequest("IMPORT_HAS_ERRORS", "预检已存在错误行，整批拒绝（DOM-24）");
        }
        handler.apply(parsed.rows());
        jdbc.update("update import_batch set status = 'APPLIED', applied_at = current_timestamp,"
                + " updated_at = current_timestamp where id = ?", batchId);
        auditService.record("import.apply", "import_batch", batchId, null,
                Map.of("kind", batch.kind(), "totalRows", parsed.totalRows()), null);
        return view(batchId, false);
    }

    @Transactional(readOnly = true)
    public String errorsCsv(UUID batchId) {
        scopeGuard.requireGlobal("import:write");
        BatchView batch = view(batchId, false);
        StringBuilder csv = new StringBuilder("\uFEFF行号,列,错误码,错误信息,原始值\n");
        for (ImportErrorView error : batch.errors()) {
            csv.append(error.rowNo()).append(',')
                    .append(escape(error.column())).append(',')
                    .append(escape(error.code())).append(',')
                    .append(escape(error.message())).append(',')
                    .append(escape(error.rawValue())).append('\n');
        }
        return csv.toString();
    }

    // ------------------------------------------------------------------ 解析与校验

    private record Parsed(List<Map<String, String>> rows, List<Integer> lineNumbers, List<RowIssue> issues,
                          int totalRows) {

        int validRows() {
            Set<Integer> invalid = new java.util.HashSet<>();
            for (RowIssue issue : issues) {
                invalid.add(issue.rowIndex());
            }
            return totalRows - invalid.size();
        }
    }

    private Parsed parseAndValidate(ImportHandler handler, byte[] bytes) {
        List<List<String>> raw = CsvParser.parse(new String(bytes, StandardCharsets.UTF_8));
        if (raw.isEmpty()) {
            throw ApiException.badRequest("IMPORT_EMPTY", "导入文件为空");
        }
        List<String> header = raw.get(0).stream().map(String::trim).toList();
        List<String> missing = handler.requiredColumns().stream().filter(column -> !header.contains(column)).toList();
        if (!missing.isEmpty()) {
            throw ApiException.badRequest("IMPORT_HEADER_MISSING", "缺少必需列：" + String.join("、", missing));
        }
        List<Map<String, String>> rows = new ArrayList<>();
        List<Integer> lineNumbers = new ArrayList<>();
        for (int index = 1; index < raw.size(); index++) {
            List<String> cells = raw.get(index);
            boolean blank = cells.stream().allMatch(cell -> cell == null || cell.isBlank());
            if (blank) {
                continue;
            }
            Map<String, String> row = new LinkedHashMap<>();
            for (int column = 0; column < header.size(); column++) {
                row.put(header.get(column), column < cells.size() ? cells.get(column) : "");
            }
            rows.add(row);
            lineNumbers.add(index + 1);
        }
        if (rows.isEmpty()) {
            throw ApiException.badRequest("IMPORT_EMPTY", "导入文件没有数据行");
        }
        if (rows.size() > MAX_ROWS) {
            throw ApiException.badRequest("IMPORT_TOO_LARGE", "单次导入不超过 " + MAX_ROWS + " 行");
        }
        List<RowIssue> issues = handler.validate(rows);
        return new Parsed(rows, lineNumbers, issues, rows.size());
    }

    private void persistErrors(UUID batchId, Parsed parsed) {
        for (RowIssue issue : parsed.issues()) {
            int line = parsed.lineNumbers().get(issue.rowIndex());
            String rawValue = issue.rawValue() == null ? null : truncate(issue.rawValue(), 500);
            jdbc.update("insert into import_row_error (id, batch_id, row_no, column_name, code, message, raw_value)"
                            + " values (?, ?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID(), batchId, line, issue.column(), issue.code(),
                    truncate(issue.message(), 500), rawValue);
        }
    }

    // ------------------------------------------------------------------ 批次读取

    private record ImportBatchRecord(UUID id, String kind, String fileName, String fileSha256, String status,
                                     int totalRows, int validRows, int invalidRows, Instant appliedAt) {
    }

    private UUID findBatch(String kind, String sha256, String status) {
        List<UUID> ids = jdbc.queryForList("select id from import_batch where kind = ? and file_sha256 = ?"
                + " and status = ? order by created_at desc", UUID.class, kind, sha256, status);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private ImportBatchRecord requireBatch(UUID batchId) {
        List<ImportBatchRecord> records = jdbc.query(
                "select id, kind, file_name, file_sha256, status, total_rows, valid_rows, invalid_rows, applied_at"
                        + " from import_batch where id = ?",
                (rs, rowNum) -> new ImportBatchRecord(rs.getObject("id", UUID.class), rs.getString("kind"),
                        rs.getString("file_name"), rs.getString("file_sha256"), rs.getString("status"),
                        rs.getInt("total_rows"), rs.getInt("valid_rows"), rs.getInt("invalid_rows"),
                        rs.getTimestamp("applied_at") == null ? null : rs.getTimestamp("applied_at").toInstant()),
                batchId);
        if (records.isEmpty()) {
            throw ApiException.notFound("导入批次不存在");
        }
        return records.get(0);
    }

    private BatchView view(UUID batchId, boolean duplicate) {
        ImportBatchRecord batch = requireBatch(batchId);
        Integer errorCount = jdbc.queryForObject("select count(*) from import_row_error where batch_id = ?",
                Integer.class, batchId);
        List<ImportErrorView> errors = new ArrayList<>();
        for (Map<String, Object> row : jdbc.queryForList("select row_no, column_name, code, message, raw_value"
                + " from import_row_error where batch_id = ? order by row_no, id", batchId)) {
            errors.add(new ImportErrorView(((Number) row.get("row_no")).intValue(), (String) row.get("column_name"),
                    (String) row.get("code"), (String) row.get("message"), (String) row.get("raw_value")));
        }
        List<ImportErrorView> capped = errors.size() > MAX_ERRORS_IN_RESPONSE
                ? errors.subList(0, MAX_ERRORS_IN_RESPONSE) : errors;
        return new BatchView(batch.id(), batch.kind(), batch.fileName(), batch.fileSha256(), batch.status(),
                batch.totalRows(), batch.validRows(), batch.invalidRows(), errorCount == null ? 0 : errorCount,
                duplicate, batch.appliedAt(), List.copyOf(capped));
    }

    // ------------------------------------------------------------------ 工具

    private ImportHandler handlerFor(String kind) {
        return handlers.stream().filter(handler -> handler.kind().equals(kind)).findFirst()
                .orElseThrow(() -> ApiException.badRequest("IMPORT_KIND_NOT_SUPPORTED",
                        "该导入类型尚未开放：" + kind));
    }

    private String normalizeKind(String kindRaw) {
        String kind = kindRaw == null ? "" : kindRaw.trim().toUpperCase(Locale.ROOT);
        if (!KINDS.contains(kind)) {
            throw ApiException.badRequest("IMPORT_KIND_INVALID", "导入类型只能是 MEMBER/ATTENDANCE/RESULT");
        }
        return kind;
    }

    private byte[] readBytes(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("IMPORT_FILE_REQUIRED", "请上传 CSV 文件");
        }
        if (file.getSize() > MAX_BYTES) {
            throw ApiException.badRequest("IMPORT_TOO_LARGE", "文件不能超过 5MB");
        }
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw ApiException.badRequest("IMPORT_FILE_UNREADABLE", "文件读取失败");
        }
    }

    private String fileName(MultipartFile file) {
        String name = file.getOriginalFilename();
        return name == null || name.isBlank() ? "upload.csv" : truncate(name, 300);
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
    }

    private String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        String escaped = value.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }

    // ------------------------------------------------------------------ 视图

    public record ImportErrorView(int rowNo, String column, String code, String message, String rawValue) {
    }

    public record BatchView(UUID id, String kind, String fileName, String fileSha256, String status,
                            int totalRows, int validRows, int invalidRows, int errorCount, boolean duplicate,
                            Instant appliedAt, List<ImportErrorView> errors) {
    }
}