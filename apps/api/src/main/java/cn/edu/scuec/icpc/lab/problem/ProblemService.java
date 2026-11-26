package cn.edu.scuec.icpc.lab.problem;

import cn.edu.scuec.icpc.lab.audit.AuditService;
import cn.edu.scuec.icpc.lab.category.AlgorithmCategory;
import cn.edu.scuec.icpc.lab.category.AlgorithmCategoryRepository;
import cn.edu.scuec.icpc.lab.common.ApiException;
import cn.edu.scuec.icpc.lab.common.PageResponse;
import cn.edu.scuec.icpc.lab.security.CurrentUser;
import cn.edu.scuec.icpc.lab.security.ScopeGuard;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 题目与标签服务（WP-P0-12 / FR-06 / DOM-19 / DOM-22 / DOM-23）。
 * 每题恰好一个主算法；同来源同标题重复必须显式拒绝，不得静默覆盖。
 */
@Service
public class ProblemService {

    private static final Set<String> TAG_TYPES = Set.of("TYPE", "SKILL", "YEAR", "LEVEL");
    private static final int MAX_PAGE_SIZE = 100;

    private final ProblemRepository problems;
    private final TagRepository tags;
    private final AlgorithmCategoryRepository categories;
    private final JdbcTemplate jdbc;
    private final ScopeGuard scopeGuard;
    private final CurrentUser currentUser;
    private final AuditService auditService;

    public ProblemService(ProblemRepository problems, TagRepository tags, AlgorithmCategoryRepository categories,
                          JdbcTemplate jdbc, ScopeGuard scopeGuard, CurrentUser currentUser, AuditService auditService) {
        this.problems = problems;
        this.tags = tags;
        this.categories = categories;
        this.jdbc = jdbc;
        this.scopeGuard = scopeGuard;
        this.currentUser = currentUser;
        this.auditService = auditService;
    }

    // ------------------------------------------------------------------ 查询

    @Transactional(readOnly = true)
    public PageResponse<ProblemView> list(String source, UUID categoryId, UUID tagId, String keyword,
                                          Boolean active, int page, int size) {
        scopeGuard.requireAnyScope("problem:read");
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder(" where 1 = 1");
        if (source != null && !source.isBlank()) {
            where.append(" and p.source_key = ?");
            params.add(source.trim().toLowerCase(Locale.ROOT));
        }
        if (active != null) {
            where.append(" and p.active = ?");
            params.add(active);
        }
        if (categoryId != null) {
            where.append(" and exists (select 1 from problem_algorithm pa where pa.problem_id = p.id"
                    + " and pa.category_id = ? and pa.active = true)");
            params.add(categoryId);
        }
        if (tagId != null) {
            where.append(" and exists (select 1 from problem_tag pt where pt.problem_id = p.id and pt.tag_id = ?)");
            params.add(tagId);
        }
        if (keyword != null && !keyword.isBlank()) {
            where.append(" and (lower(p.title) like ? or lower(coalesce(p.code, '')) like ?)");
            String like = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
            params.add(like);
            params.add(like);
        }
        Long total = jdbc.queryForObject("select count(*) from problem p" + where, Long.class, params.toArray());
        List<Object> paged = new ArrayList<>(params);
        paged.add(safeSize);
        paged.add((long) safePage * safeSize);
        List<UUID> ids = jdbc.queryForList("select p.id from problem p" + where
                + " order by p.created_at desc, p.id limit ? offset ?", UUID.class, paged.toArray());
        return new PageResponse<>(views(ids), safePage, safeSize, total == null ? 0 : total);
    }

    @Transactional(readOnly = true)
    public ProblemView get(UUID id) {
        scopeGuard.requireAnyScope("problem:read");
        return views(List.of(require(id).getId())).get(0);
    }

    // ------------------------------------------------------------------ 写入

    @Transactional
    public ProblemView create(SaveCommand command) {
        scopeGuard.requireGlobal("problem:write");
        String title = requireTitle(command.title());
        String source = normalizeSource(command.source());
        String sourceKey = source.toLowerCase(Locale.ROOT);
        String titleKey = normalizeKey(title);
        if (problems.existsBySourceKeyAndTitleKey(sourceKey, titleKey)) {
            throw ApiException.conflict("PROBLEM_DUPLICATE", "该来源下已存在同名题目，请先核对再提交（DOM-23）");
        }
        List<ValidatedAlgorithm> algorithms = validateAlgorithms(command.algorithms());
        Problem problem = new Problem();
        problem.setTitle(title);
        problem.setCode(trimToNull(command.code()));
        problem.setSource(source);
        problem.setSourceKey(sourceKey);
        problem.setTitleKey(titleKey);
        problem.setLink(trimToNull(command.link()));
        problem.setDifficulty(trimToNull(command.difficulty()));
        problem.setStatement(trimToNull(command.statement()));
        problem.setCreatedBy(currentUser.principal().personId());
        problem.setUpdatedBy(problem.getCreatedBy());
        problems.saveAndFlush(problem);
        applyAlgorithms(problem.getId(), algorithms);
        applyTags(problem.getId(), command.tagIds());
        auditService.record("problem.create", "problem", problem.getId(), null, snapshot(problem), null);
        return views(List.of(problem.getId())).get(0);
    }

    @Transactional
    public ProblemView update(UUID id, SaveCommand command) {
        scopeGuard.requireGlobal("problem:write");
        Problem problem = require(id);
        Map<String, Object> before = snapshot(problem);
        if (command.title() != null) {
            String title = requireTitle(command.title());
            String sourceKey = command.source() == null ? problem.getSourceKey() : normalizeSource(command.source()).toLowerCase(Locale.ROOT);
            String titleKey = normalizeKey(title);
            problems.findBySourceKeyAndTitleKey(sourceKey, titleKey)
                    .filter(other -> !other.getId().equals(id))
                    .ifPresent(other -> {
                        throw ApiException.conflict("PROBLEM_DUPLICATE", "该来源下已存在同名题目，请先核对再提交（DOM-23）");
                    });
            problem.setTitle(title);
            problem.setTitleKey(titleKey);
        }
        if (command.source() != null) {
            String source = normalizeSource(command.source());
            problem.setSource(source);
            problem.setSourceKey(source.toLowerCase(Locale.ROOT));
        }
        if (command.code() != null) {
            problem.setCode(trimToNull(command.code()));
        }
        if (command.link() != null) {
            problem.setLink(trimToNull(command.link()));
        }
        if (command.difficulty() != null) {
            problem.setDifficulty(trimToNull(command.difficulty()));
        }
        if (command.statement() != null) {
            problem.setStatement(trimToNull(command.statement()));
        }
        problem.setUpdatedBy(currentUser.principal().personId());
        problems.save(problem);
        if (command.algorithms() != null) {
            applyAlgorithms(id, validateAlgorithms(command.algorithms()));
        }
        applyTags(id, command.tagIds());
        auditService.record("problem.update", "problem", id, before, snapshot(problem), command.reason());
        return views(List.of(id)).get(0);
    }

    @Transactional
    public ProblemView deactivate(UUID id, String reason) {
        scopeGuard.requireGlobal("problem:write");
        Problem problem = require(id);
        if (!problem.isActive()) {
            return views(List.of(id)).get(0);
        }
        Map<String, Object> before = snapshot(problem);
        problem.setActive(false);
        problem.setUpdatedBy(currentUser.principal().personId());
        problems.save(problem);
        auditService.record("problem.deactivate", "problem", id, before, snapshot(problem), reason);
        return views(List.of(id)).get(0);
    }

    private void applyAlgorithms(UUID problemId, List<ValidatedAlgorithm> algorithms) {
        jdbc.update("update problem_algorithm set is_primary = false, primary_key = null"
                + " where problem_id = ? and primary_key is not null", problemId);
        Map<UUID, UUID> existing = new LinkedHashMap<>();
        for (Map<String, Object> row : jdbc.queryForList(
                "select id, category_id from problem_algorithm where problem_id = ?", problemId)) {
            existing.put((UUID) row.get("category_id"), (UUID) row.get("id"));
        }
        Set<UUID> desired = new HashSet<>();
        for (ValidatedAlgorithm algorithm : algorithms) {
            desired.add(algorithm.categoryId());
            UUID rowId = existing.get(algorithm.categoryId());
            if (rowId == null) {
                jdbc.update("insert into problem_algorithm (id, problem_id, category_id, is_primary, primary_key, active)"
                                + " values (?, ?, ?, ?, ?, true)",
                        UUID.randomUUID(), problemId, algorithm.categoryId(), algorithm.primary(),
                        algorithm.primary() ? problemId : null);
            } else {
                jdbc.update("update problem_algorithm set active = true, is_primary = ?, primary_key = ? where id = ?",
                        algorithm.primary(), algorithm.primary() ? problemId : null, rowId);
            }
        }
        for (Map.Entry<UUID, UUID> row : existing.entrySet()) {
            if (!desired.contains(row.getKey())) {
                jdbc.update("update problem_algorithm set active = false, is_primary = false, primary_key = null"
                        + " where id = ?", row.getValue());
            }
        }
    }

    private void applyTags(UUID problemId, List<UUID> tagIds) {
        if (tagIds == null) {
            return;
        }
        Set<UUID> unique = new HashSet<>();
        for (UUID tagId : tagIds) {
            if (tagId == null) {
                throw ApiException.badRequest("VALIDATION_ERROR", "标签 id 不能为空");
            }
            if (!unique.add(tagId)) {
                continue;
            }
            Tag tag = tags.findById(tagId).orElseThrow(() -> ApiException.badRequest("TAG_NOT_FOUND", "标签不存在：" + tagId));
            if (!tag.isActive()) {
                throw ApiException.badRequest("TAG_INACTIVE", "标签已停用：" + tag.getName());
            }
        }
        jdbc.update("delete from problem_tag where problem_id = ?", problemId);
        for (UUID tagId : unique) {
            jdbc.update("insert into problem_tag (problem_id, tag_id) values (?, ?)", problemId, tagId);
        }
    }

    private List<ValidatedAlgorithm> validateAlgorithms(List<AlgorithmInput> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            throw ApiException.badRequest("PRIMARY_ALGORITHM_REQUIRED", "每题必须指定恰好一个主算法（DOM-19）");
        }
        Set<UUID> seen = new HashSet<>();
        List<ValidatedAlgorithm> result = new ArrayList<>();
        int primaries = 0;
        for (AlgorithmInput input : inputs) {
            if (input == null || input.categoryId() == null) {
                throw ApiException.badRequest("VALIDATION_ERROR", "算法分类不能为空");
            }
            if (!seen.add(input.categoryId())) {
                throw ApiException.badRequest("VALIDATION_ERROR", "同一算法分类重复出现");
            }
            AlgorithmCategory category = categories.findById(input.categoryId())
                    .orElseThrow(() -> ApiException.badRequest("CATEGORY_NOT_FOUND", "算法分类不存在"));
            if (!category.isActive()) {
                throw ApiException.badRequest("CATEGORY_INACTIVE", "算法分类已停用：" + category.getName());
            }
            boolean primary = Boolean.TRUE.equals(input.primary());
            if (primary) {
                primaries++;
            }
            result.add(new ValidatedAlgorithm(input.categoryId(), primary));
        }
        if (primaries == 0) {
            throw ApiException.badRequest("PRIMARY_ALGORITHM_REQUIRED", "每题必须指定恰好一个主算法（DOM-19）");
        }
        if (primaries > 1) {
            throw ApiException.badRequest("MULTIPLE_PRIMARY_ALGORITHMS", "每题只能有一个主算法（DOM-19）");
        }
        return result;
    }

    // ------------------------------------------------------------------ 标签

    @Transactional(readOnly = true)
    public List<TagView> listTags(String type) {
        scopeGuard.requireAnyScope("problem:read");
        List<Tag> result = type == null || type.isBlank()
                ? tags.findAllByOrderByNameAsc()
                : tags.findAllByTypeOrderByNameAsc(type.trim().toUpperCase(Locale.ROOT));
        return result.stream().map(this::toTagView).toList();
    }

    @Transactional
    public TagView createTag(TagCommand command) {
        scopeGuard.requireGlobal("category:write");
        String name = command.name() == null ? "" : command.name().trim();
        if (name.isEmpty() || name.length() > 64) {
            throw ApiException.badRequest("VALIDATION_ERROR", "标签名称必填且不超过 64 字");
        }
        String type = command.type() == null ? "TYPE" : command.type().trim().toUpperCase(Locale.ROOT);
        if (!TAG_TYPES.contains(type)) {
            throw ApiException.badRequest("TAG_TYPE_INVALID", "标签类型只能是 TYPE/SKILL/YEAR/LEVEL");
        }
        if (tags.existsByNameAndType(name, type)) {
            throw ApiException.conflict("TAG_EXISTS", "标签已存在：" + name);
        }
        Tag tag = new Tag();
        tag.setName(name);
        tag.setType(type);
        tags.save(tag);
        auditService.record("tag.create", "tag", tag.getId(), null, toTagSnapshot(tag), null);
        return toTagView(tag);
    }

    @Transactional
    public TagView deactivateTag(UUID id) {
        scopeGuard.requireGlobal("category:write");
        Tag tag = tags.findById(id).orElseThrow(() -> ApiException.notFound("标签不存在"));
        if (tag.isActive()) {
            Map<String, Object> before = toTagSnapshot(tag);
            tag.setActive(false);
            tags.save(tag);
            auditService.record("tag.deactivate", "tag", id, before, toTagSnapshot(tag), null);
        }
        return toTagView(tag);
    }

    // ------------------------------------------------------------------ 视图

    private List<ProblemView> views(List<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<AlgorithmRow>> algorithmsByProblem = new LinkedHashMap<>();
        Map<UUID, List<TagView>> tagsByProblem = new LinkedHashMap<>();
        String in = String.join(",", ids.stream().map(id -> "?").toList());
        for (Map<String, Object> row : jdbc.queryForList(
                "select pa.problem_id, pa.category_id, pa.is_primary, ac.name as category_name,"
                        + " ac.code as category_code from problem_algorithm pa"
                        + " join algorithm_category ac on ac.id = pa.category_id"
                        + " where pa.problem_id in (" + in + ") and pa.active = true"
                        + " order by pa.is_primary desc, ac.name", ids.toArray())) {
            UUID problemId = (UUID) row.get("problem_id");
            algorithmsByProblem.computeIfAbsent(problemId, key -> new ArrayList<>()).add(new AlgorithmRow(
                    (UUID) row.get("category_id"), (String) row.get("category_code"),
                    (String) row.get("category_name"), Boolean.TRUE.equals(row.get("is_primary"))));
        }
        for (Map<String, Object> row : jdbc.queryForList(
                "select pt.problem_id, t.id, t.name, t.type, t.active from problem_tag pt"
                        + " join tag t on t.id = pt.tag_id where pt.problem_id in (" + in + ") order by t.name",
                ids.toArray())) {
            UUID problemId = (UUID) row.get("problem_id");
            tagsByProblem.computeIfAbsent(problemId, key -> new ArrayList<>()).add(new TagView(
                    (UUID) row.get("id"), (String) row.get("name"), (String) row.get("type"),
                    Boolean.TRUE.equals(row.get("active"))));
        }
        List<ProblemView> views = new ArrayList<>();
        for (UUID id : ids) {
            Problem problem = require(id);
            views.add(new ProblemView(problem.getId(), problem.getCode(), problem.getTitle(), problem.getSource(),
                    problem.getLink(), problem.getDifficulty(), problem.getStatement(), problem.isActive(),
                    problem.getCreatedBy(), problem.getUpdatedBy(),
                    algorithmsByProblem.getOrDefault(id, List.of()), tagsByProblem.getOrDefault(id, List.of())));
        }
        return views;
    }

    private Problem require(UUID id) {
        return problems.findById(id).orElseThrow(() -> ApiException.notFound("题目不存在"));
    }

    private String requireTitle(String title) {
        String value = title == null ? "" : title.trim();
        if (value.isEmpty() || value.length() > 300) {
            throw ApiException.badRequest("VALIDATION_ERROR", "题目标题必填且不超过 300 字");
        }
        return value;
    }

    private String normalizeSource(String source) {
        String value = source == null || source.isBlank() ? "OTHER" : source.trim();
        if (value.length() > 64) {
            throw ApiException.badRequest("VALIDATION_ERROR", "题目来源不超过 64 字");
        }
        return value;
    }

    private String normalizeKey(String value) {
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Map<String, Object> snapshot(Problem problem) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("title", problem.getTitle());
        map.put("code", problem.getCode());
        map.put("source", problem.getSource());
        map.put("difficulty", problem.getDifficulty());
        map.put("active", problem.isActive());
        return map;
    }

    private Map<String, Object> toTagSnapshot(Tag tag) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", tag.getName());
        map.put("type", tag.getType());
        map.put("active", tag.isActive());
        return map;
    }

    private TagView toTagView(Tag tag) {
        return new TagView(tag.getId(), tag.getName(), tag.getType(), tag.isActive());
    }

    // ------------------------------------------------------------------ 命令与视图

    public record AlgorithmInput(UUID categoryId, Boolean primary) {
    }

    public record SaveCommand(String code, String title, String source, String link, String difficulty,
                              String statement, List<AlgorithmInput> algorithms, List<UUID> tagIds, String reason) {
    }

    public record TagCommand(String name, String type) {
    }

    public record AlgorithmRow(UUID categoryId, String categoryCode, String categoryName, boolean primary) {
    }

    public record TagView(UUID id, String name, String type, boolean active) {
    }

    public record ProblemView(UUID id, String code, String title, String source, String link, String difficulty,
                              String statement, boolean active, UUID createdBy, UUID updatedBy,
                              List<AlgorithmRow> algorithms, List<TagView> tags) {
    }

    private record ValidatedAlgorithm(UUID categoryId, boolean primary) {
    }
}