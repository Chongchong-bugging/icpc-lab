package cn.edu.scuec.icpc.lab.trainingplan;

import cn.edu.scuec.icpc.lab.approval.ApprovalInstance;
import cn.edu.scuec.icpc.lab.approval.ApprovalService;
import cn.edu.scuec.icpc.lab.audit.AuditService;
import cn.edu.scuec.icpc.lab.common.ApiException;
import cn.edu.scuec.icpc.lab.common.PageResponse;
import cn.edu.scuec.icpc.lab.org.SemesterRepository;
import cn.edu.scuec.icpc.lab.org.TermRepository;
import cn.edu.scuec.icpc.lab.security.CurrentUser;
import cn.edu.scuec.icpc.lab.security.ScopeGuard;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 训练计划（WP-P0-14 / FR-38 / FR-40 / BR-05 / BR-21 / DOM-10）。
 * DRAFT → SUBMITTED（审批实例）→ APPROVED → PUBLISHED；已发布内容不可改写，修订生成新版本。
 */
@Service
public class TrainingPlanService {

    public static final String TRAINING_PLAN = "TRAINING_PLAN";
    private static final Set<String> KINDS = Set.of(TrainingPlan.SEMESTER, TrainingPlan.WINTER, TrainingPlan.SUMMER);
    private static final Set<String> STATUSES = Set.of(TrainingPlan.DRAFT, TrainingPlan.SUBMITTED,
            TrainingPlan.APPROVED, TrainingPlan.PUBLISHED, TrainingPlan.ARCHIVED);

    private final TrainingPlanRepository plans;
    private final TrainingPlanVersionRepository versions;
    private final SemesterRepository semesters;
    private final TermRepository terms;
    private final ApprovalService approvalService;
    private final ScopeGuard scopeGuard;
    private final CurrentUser currentUser;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbc;

    public TrainingPlanService(TrainingPlanRepository plans, TrainingPlanVersionRepository versions,
                               SemesterRepository semesters, TermRepository terms,
                               ApprovalService approvalService, ScopeGuard scopeGuard, CurrentUser currentUser,
                               AuditService auditService, ObjectMapper objectMapper, JdbcTemplate jdbc) {
        this.plans = plans;
        this.versions = versions;
        this.semesters = semesters;
        this.terms = terms;
        this.approvalService = approvalService;
        this.scopeGuard = scopeGuard;
        this.currentUser = currentUser;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------------ 查询

    @Transactional(readOnly = true)
    public PageResponse<PlanView> list(UUID termId, UUID semesterId, String status, int page, int size) {
        scopeGuard.requireAnyScope("plan:read");
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 100);
        StringBuilder where = new StringBuilder(" where 1 = 1");
        List<Object> args = new ArrayList<>();
        if (termId != null) {
            where.append(" and term_id = ?");
            args.add(termId);
        }
        if (semesterId != null) {
            where.append(" and semester_id = ?");
            args.add(semesterId);
        }
        if (status != null && !status.isBlank()) {
            String normalized = status.trim().toUpperCase();
            if (!STATUSES.contains(normalized)) {
                throw ApiException.badRequest("PLAN_STATUS_INVALID", "计划状态只能是 DRAFT/SUBMITTED/APPROVED/PUBLISHED/ARCHIVED");
            }
            where.append(" and status = ?");
            args.add(normalized);
        }
        Long total = jdbc.queryForObject("select count(*) from training_plan" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(safeSize);
        pageArgs.add((long) safePage * safeSize);
        List<PlanView> items = jdbc.query("select id, term_id, semester_id, kind, title, status, current_version,"
                        + " content, published_at, published_by, created_by, updated_by, created_at, updated_at"
                        + " from training_plan" + where + " order by created_at desc, id limit ? offset ?",
                (rs, rowNum) -> new PlanView(rs.getObject("id", UUID.class), rs.getObject("term_id", UUID.class),
                        rs.getObject("semester_id", UUID.class), rs.getString("kind"), rs.getString("title"),
                        rs.getString("status"), rs.getInt("current_version"), readJson(rs.getString("content")),
                        rs.getTimestamp("published_at") == null ? null : rs.getTimestamp("published_at").toInstant(),
                        rs.getObject("published_by", UUID.class), rs.getObject("created_by", UUID.class),
                        rs.getObject("updated_by", UUID.class),
                        rs.getTimestamp("created_at") == null ? null : rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("updated_at") == null ? null : rs.getTimestamp("updated_at").toInstant()),
                pageArgs.toArray());
        return new PageResponse<>(items, safePage, safeSize, total == null ? 0 : total);
    }

    @Transactional(readOnly = true)
    public PlanView get(UUID id) {
        scopeGuard.requireAnyScope("plan:read");
        return view(require(id));
    }

    @Transactional(readOnly = true)
    public List<VersionView> versions(UUID id) {
        scopeGuard.requireAnyScope("plan:read");
        require(id);
        return versions.findAllByPlanIdOrderByVersionNoAsc(id).stream()
                .map(version -> new VersionView(version.getVersionNo(), readJson(version.getSnapshot()),
                        version.getChangeSummary(), version.getCreatedBy(), version.getCreatedAt()))
                .toList();
    }

    // ------------------------------------------------------------------ 写入

    @Transactional
    public PlanView create(CreateCommand command) {
        scopeGuard.requireGlobal("plan:write");
        String kind = command.kind() == null || command.kind().isBlank()
                ? TrainingPlan.SEMESTER : command.kind().trim().toUpperCase();
        if (!KINDS.contains(kind)) {
            throw ApiException.badRequest("PLAN_KIND_INVALID", "计划类型只能是 SEMESTER/WINTER/SUMMER");
        }
        String title = requireTitle(command.title());
        validateScope(command.termId(), command.semesterId());
        String content = serialize(command.content());
        UUID personId = currentUser.principal().personId();

        TrainingPlan plan = new TrainingPlan();
        plan.setTermId(command.termId());
        plan.setSemesterId(command.semesterId());
        plan.setKind(kind);
        plan.setTitle(title);
        plan.setStatus(TrainingPlan.DRAFT);
        plan.setCurrentVersion(1);
        plan.setContent(content);
        plan.setCreatedBy(personId);
        plan.setUpdatedBy(personId);
        plans.save(plan);
        writeVersion(plan, defaultSummary(command.changeSummary(), "创建"));
        auditService.record("plan.create", "training_plan", plan.getId(), null,
                Map.of("title", title, "kind", kind), command.reason());
        return view(plan);
    }

    @Transactional
    public PlanView update(UUID id, UpdateCommand command) {
        scopeGuard.requireGlobal("plan:write");
        TrainingPlan plan = require(id);
        assertEditable(plan);
        String title = requireTitle(command.title());
        String content = serialize(command.content());
        Map<String, Object> before = Map.of("title", plan.getTitle(), "status", plan.getStatus(),
                "currentVersion", plan.getCurrentVersion());
        if (TrainingPlan.APPROVED.equals(plan.getStatus())) {
            plan.setStatus(TrainingPlan.DRAFT);
        }
        plan.setTitle(title);
        plan.setContent(content);
        plan.setCurrentVersion(plan.getCurrentVersion() + 1);
        plan.setUpdatedBy(currentUser.principal().personId());
        plans.save(plan);
        writeVersion(plan, defaultSummary(command.changeSummary(), "修改"));
        auditService.record("plan.update", "training_plan", plan.getId(), before,
                Map.of("title", title, "status", plan.getStatus(), "currentVersion", plan.getCurrentVersion()),
                command.reason());
        return view(plan);
    }

    @Transactional
    public PlanView submit(UUID id, String reason) {
        scopeGuard.requireGlobal("plan:write");
        TrainingPlan plan = require(id);
        if (!TrainingPlan.DRAFT.equals(plan.getStatus())) {
            throw ApiException.conflict("PLAN_NOT_DRAFT", "只有草稿状态的计划可以提交审批");
        }
        assertSubmitDeadline(plan);
        plan.setStatus(TrainingPlan.SUBMITTED);
        plan.setUpdatedBy(currentUser.principal().personId());
        plans.save(plan);
        approvalService.open(TRAINING_PLAN, plan.getId(), plan.getTitle(), 1);
        auditService.record("plan.submit", "training_plan", plan.getId(), Map.of("status", TrainingPlan.DRAFT),
                Map.of("status", TrainingPlan.SUBMITTED), reason);
        return view(plan);
    }

    @Transactional
    public PlanView approve(UUID id, String decision, String comment) {
        scopeGuard.requireGlobal("plan:approve");
        TrainingPlan plan = require(id);
        if (!TrainingPlan.SUBMITTED.equals(plan.getStatus())) {
            throw ApiException.conflict("PLAN_NOT_SUBMITTED", "只有待审批状态的计划可以审批");
        }
        ApprovalInstance instance = approvalService.requirePending(TRAINING_PLAN, id);
        approvalService.decide(instance, currentUser.principal().personId(), decision, comment);
        String status = ApprovalInstance.APPROVED.equals(instance.getStatus())
                ? TrainingPlan.APPROVED : TrainingPlan.DRAFT;
        plan.setStatus(status);
        plan.setUpdatedBy(currentUser.principal().personId());
        plans.save(plan);
        auditService.record("plan." + status.toLowerCase(), "training_plan", plan.getId(),
                Map.of("status", TrainingPlan.SUBMITTED), Map.of("status", status), comment);
        return view(plan);
    }

    @Transactional
    public PlanView publish(UUID id, String reason) {
        scopeGuard.requireGlobal("plan:publish");
        TrainingPlan plan = require(id);
        if (!TrainingPlan.APPROVED.equals(plan.getStatus())) {
            throw ApiException.conflict("PLAN_NOT_APPROVED", "只有审批通过的计划可以发布");
        }
        plan.setStatus(TrainingPlan.PUBLISHED);
        plan.setPublishedAt(Instant.now());
        plan.setPublishedBy(currentUser.principal().personId());
        plan.setUpdatedBy(currentUser.principal().personId());
        plans.save(plan);
        // 发布只改变状态，内容未变：不重复写入同版本号快照（uq_training_plan_version 保证版本号唯一）。
        auditService.record("plan.publish", "training_plan", plan.getId(),
                Map.of("status", TrainingPlan.APPROVED), Map.of("status", TrainingPlan.PUBLISHED), reason);
        return view(plan);
    }

    @Transactional
    public PlanView revise(UUID id, String changeSummary) {
        scopeGuard.requireGlobal("plan:write");
        TrainingPlan plan = require(id);
        if (!TrainingPlan.PUBLISHED.equals(plan.getStatus())) {
            throw ApiException.conflict("PLAN_NOT_PUBLISHED", "只有已发布的计划可以新建修订版本");
        }
        plan.setStatus(TrainingPlan.DRAFT);
        plan.setCurrentVersion(plan.getCurrentVersion() + 1);
        plan.setUpdatedBy(currentUser.principal().personId());
        plans.save(plan);
        writeVersion(plan, defaultSummary(changeSummary, "新建修订版本"));
        auditService.record("plan.revise", "training_plan", plan.getId(),
                Map.of("status", TrainingPlan.PUBLISHED), Map.of("status", TrainingPlan.DRAFT), changeSummary);
        return view(plan);
    }

    // ------------------------------------------------------------------ 规则与校验

    /** BR-05：学期计划须在开学后 N 周内提交（阈值取 rule_config，不硬编码）。 */
    private void assertSubmitDeadline(TrainingPlan plan) {
        if (!TrainingPlan.SEMESTER.equals(plan.getKind()) || plan.getSemesterId() == null) {
            return;
        }
        var semester = semesters.findById(plan.getSemesterId()).orElse(null);
        if (semester == null || semester.getStartDate() == null) {
            return;
        }
        LocalDate deadline = semester.getStartDate().plusWeeks(submitDeadlineWeeks());
        if (LocalDate.now().isAfter(deadline)) {
            throw ApiException.badRequest("PLAN_SUBMIT_DEADLINE_PASSED",
                    "已超过开学后 " + submitDeadlineWeeks() + " 周的提交截止日（" + deadline + "）");
        }
    }

    private int submitDeadlineWeeks() {
        List<String> values = jdbc.queryForList("select rule_value from rule_config where rule_key = ?",
                String.class, "plan.submit_deadline_weeks");
        if (values.isEmpty() || values.get(0) == null || values.get(0).isBlank()) {
            return 2;
        }
        try {
            return Integer.parseInt(values.get(0).trim());
        } catch (NumberFormatException ex) {
            return 2;
        }
    }

    private void validateScope(UUID termId, UUID semesterId) {
        if (termId != null && !terms.existsById(termId)) {
            throw ApiException.badRequest("TERM_NOT_FOUND", "学期不存在");
        }
        if (semesterId != null && !semesters.existsById(semesterId)) {
            throw ApiException.badRequest("SEMESTER_NOT_FOUND", "教学学期不存在");
        }
    }

    private void assertEditable(TrainingPlan plan) {
        switch (plan.getStatus()) {
            case TrainingPlan.PUBLISHED -> throw ApiException.conflict("PLAN_PUBLISHED_IMMUTABLE",
                    "已发布的计划不可直接修改，请新建修订版本");
            case TrainingPlan.SUBMITTED -> throw ApiException.conflict("PLAN_SUBMITTED_IMMUTABLE",
                    "待审批的计划不可修改");
            case TrainingPlan.ARCHIVED -> throw ApiException.conflict("PLAN_ARCHIVED", "已归档的计划不可修改");
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ 内容

    private String serialize(Content content) {
        if (content == null) {
            return null;
        }
        if (content.goal() != null && content.goal().length() > 1000) {
            throw ApiException.badRequest("PLAN_CONTENT_INVALID", "训练目标不超过 1000 字");
        }
        if (content.weeklyFrequency() != null
                && (content.weeklyFrequency() < 1 || content.weeklyFrequency() > 14)) {
            throw ApiException.badRequest("PLAN_CONTENT_INVALID", "每周训练次数应在 1~14 之间");
        }
        if (content.sessionMinutes() != null
                && (content.sessionMinutes() < 30 || content.sessionMinutes() > 600)) {
            throw ApiException.badRequest("PLAN_CONTENT_INVALID", "单次训练时长应在 30~600 分钟之间");
        }
        if (content.items() != null) {
            if (content.items().size() > 50) {
                throw ApiException.badRequest("PLAN_CONTENT_INVALID", "训练条目不超过 50 条");
            }
            Set<String> seen = new HashSet<>();
            for (Item item : content.items()) {
                if (item == null || item.title() == null || item.title().isBlank()) {
                    throw ApiException.badRequest("PLAN_CONTENT_INVALID", "训练条目标题不能为空");
                }
                if (!seen.add(item.title().trim())) {
                    throw ApiException.badRequest("PLAN_CONTENT_INVALID", "训练条目标题重复：" + item.title());
                }
                if (item.note() != null && item.note().length() > 500) {
                    throw ApiException.badRequest("PLAN_CONTENT_INVALID", "条目备注不超过 500 字");
                }
            }
        }
        try {
            return objectMapper.writeValueAsString(content);
        } catch (JsonProcessingException ex) {
            throw ApiException.badRequest("PLAN_CONTENT_INVALID", "训练内容无法序列化");
        }
    }

    private JsonNode readJson(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(text);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private String requireTitle(String title) {
        if (title == null || title.isBlank()) {
            throw ApiException.badRequest("PLAN_TITLE_REQUIRED", "计划标题不能为空");
        }
        String trimmed = title.trim();
        if (trimmed.length() > 200) {
            throw ApiException.badRequest("PLAN_TITLE_TOO_LONG", "计划标题不超过 200 字");
        }
        return trimmed;
    }

    private String defaultSummary(String summary, String fallback) {
        return summary == null || summary.isBlank() ? fallback : summary.trim();
    }

    // ------------------------------------------------------------------ 内部

    private TrainingPlan require(UUID id) {
        return plans.findById(id).orElseThrow(() -> ApiException.notFound("训练计划不存在"));
    }

    private void writeVersion(TrainingPlan plan, String summary) {
        TrainingPlanVersion version = new TrainingPlanVersion();
        version.setPlanId(plan.getId());
        version.setVersionNo(plan.getCurrentVersion());
        version.setSnapshot(plan.getContent());
        version.setChangeSummary(summary.length() > 500 ? summary.substring(0, 500) : summary);
        version.setCreatedBy(currentUser.principal().personId());
        versions.save(version);
    }

    private PlanView view(TrainingPlan plan) {
        return new PlanView(plan.getId(), plan.getTermId(), plan.getSemesterId(), plan.getKind(), plan.getTitle(),
                plan.getStatus(), plan.getCurrentVersion(), readJson(plan.getContent()), plan.getPublishedAt(),
                plan.getPublishedBy(), plan.getCreatedBy(), plan.getUpdatedBy(), plan.getCreatedAt(),
                plan.getUpdatedAt());
    }

    // ------------------------------------------------------------------ 请求与视图

    public record CreateCommand(UUID termId, UUID semesterId, String kind, String title, Content content,
                                String changeSummary, String reason) {
    }

    public record UpdateCommand(String title, Content content, String changeSummary, String reason) {
    }

    public record Content(String goal, Integer weeklyFrequency, Integer sessionMinutes, List<Item> items) {
    }

    public record Item(String title, String note) {
    }

    public record PlanView(UUID id, UUID termId, UUID semesterId, String kind, String title, String status,
                           int currentVersion, JsonNode content, Instant publishedAt, UUID publishedBy,
                           UUID createdBy, UUID updatedBy, Instant createdAt, Instant updatedAt) {
    }

    public record VersionView(int versionNo, JsonNode content, String changeSummary, UUID createdBy,
                              java.time.OffsetDateTime createdAt) {
    }
}