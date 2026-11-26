package cn.edu.scuec.icpc.lab.category;

import cn.edu.scuec.icpc.lab.audit.AuditService;
import cn.edu.scuec.icpc.lab.common.ApiException;
import cn.edu.scuec.icpc.lab.identity.PersonRepository;
import cn.edu.scuec.icpc.lab.security.CurrentUser;
import cn.edu.scuec.icpc.lab.security.ScopeGuard;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 算法目录服务（WP-P0-11 / FR-37 / DOM-18 / DOM-21）。
 * 目录固定三级（stage 1..3），同级重名拒绝，被引用的分类只能停用或合并。
 */
@Service
public class CategoryService {

    private static final int MAX_DEPTH = 3;
    private static final Set<String> VALUE_TYPES = Set.of("TEXT", "NUMBER", "BOOLEAN");

    private final AlgorithmCategoryRepository categories;
    private final CategoryAttributeRepository attributes;
    private final CategoryRelationRepository relations;
    private final PersonRepository persons;
    private final JdbcTemplate jdbc;
    private final ScopeGuard scopeGuard;
    private final CurrentUser currentUser;
    private final AuditService auditService;

    public CategoryService(AlgorithmCategoryRepository categories,
                           CategoryAttributeRepository attributes,
                           CategoryRelationRepository relations,
                           PersonRepository persons,
                           JdbcTemplate jdbc,
                           ScopeGuard scopeGuard,
                           CurrentUser currentUser,
                           AuditService auditService) {
        this.categories = categories;
        this.attributes = attributes;
        this.relations = relations;
        this.persons = persons;
        this.jdbc = jdbc;
        this.scopeGuard = scopeGuard;
        this.currentUser = currentUser;
        this.auditService = auditService;
    }

    // ------------------------------------------------------------------ 查询

    @Transactional(readOnly = true)
    public List<CategoryNode> tree(UUID parentId, boolean includeInactive) {
        scopeGuard.requireAnyScope("problem:read");
        List<AlgorithmCategory> all = includeInactive
                ? categories.findAllByOrderBySortOrderAscNameAsc()
                : categories.findAllByOrderBySortOrderAscNameAsc().stream().filter(AlgorithmCategory::isActive).toList();
        Map<UUID, List<AlgorithmCategory>> byParent = new LinkedHashMap<>();
        for (AlgorithmCategory category : all) {
            byParent.computeIfAbsent(category.getParentId(), key -> new ArrayList<>()).add(category);
        }
        List<AlgorithmCategory> roots = byParent.getOrDefault(parentId, List.of());
        return roots.stream().map(node -> toNode(node, byParent, 1)).toList();
    }

    @Transactional(readOnly = true)
    public List<CategoryView> list(UUID parentId, boolean includeInactive) {
        scopeGuard.requireAnyScope("problem:read");
        List<AlgorithmCategory> all = categories.findAllByOrderBySortOrderAscNameAsc();
        return all.stream()
                .filter(category -> parentId == null || Objects.equals(parentId, category.getParentId()))
                .filter(category -> includeInactive || category.isActive())
                .map(this::toView)
                .toList();
    }

    @Transactional(readOnly = true)
    public CategoryView get(UUID id) {
        scopeGuard.requireAnyScope("problem:read");
        return toView(require(id));
    }

    // ------------------------------------------------------------------ 写入

    @Transactional
    public CategoryView create(CreateCommand command) {
        scopeGuard.requireGlobal("category:write");
        AlgorithmCategory parent = command.parentId() == null ? null : require(command.parentId());
        if (parent != null && !parent.isActive()) {
            throw ApiException.conflict("CATEGORY_PARENT_INACTIVE", "上级分类已停用，不能新增子分类");
        }
        int depth = parent == null ? 1 : depthOf(parent) + 1;
        if (depth > MAX_DEPTH) {
            throw ApiException.badRequest("CATEGORY_DEPTH_EXCEEDED", "算法目录最多三级");
        }
        String name = requireName(command.name());
        String nameKey = nameKey(command.parentId(), name);
        if (categories.findByNameKey(nameKey).isPresent()) {
            throw ApiException.conflict("CATEGORY_NAME_EXISTS", "同级已存在同名分类：" + name);
        }
        String code = normalizeCode(command.code(), name);
        if (categories.findByCode(code).isPresent()) {
            throw ApiException.conflict("CATEGORY_CODE_EXISTS", "分类编码已存在：" + code);
        }
        AlgorithmCategory category = new AlgorithmCategory();
        category.setCode(code);
        category.setName(name);
        category.setNameKey(nameKey);
        category.setParentId(command.parentId());
        category.setShortName(trimToNull(command.shortName()));
        category.setDescription(trimToNull(command.description()));
        category.setStage(depth);
        category.setSuggestedDifficulty(trimToNull(command.suggestedDifficulty()));
        category.setSortOrder(command.sortOrder() == null ? 0 : command.sortOrder());
        category.setOwnerPersonId(requirePerson(command.ownerPersonId()));
        categories.save(category);
        auditService.record("category.create", "algorithm_category", category.getId(), null, snapshot(category), null);
        return toView(category);
    }

    @Transactional
    public CategoryView update(UUID id, UpdateCommand command) {
        scopeGuard.requireGlobal("category:write");
        AlgorithmCategory category = require(id);
        if (!category.isActive()) {
            throw ApiException.conflict("CATEGORY_INACTIVE", "分类已停用，不能修改");
        }
        Map<String, Object> before = snapshot(category);
        if (command.name() != null) {
            String name = requireName(command.name());
            String nameKey = nameKey(category.getParentId(), name);
            categories.findByNameKey(nameKey)
                    .filter(other -> !other.getId().equals(id))
                    .ifPresent(other -> {
                        throw ApiException.conflict("CATEGORY_NAME_EXISTS", "同级已存在同名分类：" + name);
                    });
            category.setName(name);
            category.setNameKey(nameKey);
        }
        if (command.shortName() != null) {
            category.setShortName(trimToNull(command.shortName()));
        }
        if (command.description() != null) {
            category.setDescription(trimToNull(command.description()));
        }
        if (command.suggestedDifficulty() != null) {
            category.setSuggestedDifficulty(trimToNull(command.suggestedDifficulty()));
        }
        if (command.sortOrder() != null) {
            category.setSortOrder(command.sortOrder());
        }
        if (command.ownerPersonId() != null) {
            category.setOwnerPersonId(requirePerson(command.ownerPersonId()));
        }
        categories.save(category);
        auditService.record("category.update", "algorithm_category", id, before, snapshot(category), command.reason());
        return toView(category);
    }

    @Transactional
    public CategoryView deactivate(UUID id, String reason) {
        scopeGuard.requireGlobal("category:write");
        AlgorithmCategory category = require(id);
        if (!category.isActive()) {
            return toView(category);
        }
        if (categories.countByParentIdAndActiveTrue(id) > 0) {
            throw ApiException.conflict("CATEGORY_HAS_CHILDREN", "存在启用中的子分类，不能停用");
        }
        Map<String, Object> before = snapshot(category);
        category.setActive(false);
        categories.save(category);
        auditService.record("category.deactivate", "algorithm_category", id, before, snapshot(category), reason);
        return toView(category);
    }

    /**
     * 合并：目标必须同级且启用；源置停用并记录 mergedIntoId，题目关联/属性/前置关系迁移到目标（DOM-21）。
     */
    @Transactional
    public CategoryView merge(UUID sourceId, UUID targetId, String reason) {
        scopeGuard.requireGlobal("category:write");
        if (sourceId.equals(targetId)) {
            throw ApiException.badRequest("CATEGORY_MERGE_SELF", "不能把分类合并到自身");
        }
        AlgorithmCategory source = require(sourceId);
        AlgorithmCategory target = require(targetId);
        if (!source.isActive() || !target.isActive()) {
            throw ApiException.conflict("CATEGORY_INACTIVE", "停用的分类不能参与合并");
        }
        if (!Objects.equals(source.getParentId(), target.getParentId())) {
            throw ApiException.badRequest("CATEGORY_MERGE_NOT_SIBLING", "只能合并同级分类");
        }
        if (categories.countByParentIdAndActiveTrue(sourceId) > 0) {
            throw ApiException.conflict("CATEGORY_HAS_CHILDREN", "源分类仍有启用中的子分类，请先处理");
        }
        Map<String, Object> before = snapshot(source);

        migrateAlgorithmRows(sourceId, targetId);
        migrateAttributes(sourceId, targetId);
        migrateRelations(sourceId, targetId);

        source.setActive(false);
        source.setMergedIntoId(targetId);
        categories.save(source);
        auditService.record("category.merge", "algorithm_category", sourceId,
                before, snapshot(source), reason == null ? "合并到 " + target.getName() : reason);
        return toView(target);
    }

    private void migrateAlgorithmRows(UUID sourceId, UUID targetId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select id, problem_id, is_primary from problem_algorithm where category_id = ? and active = true", sourceId);
        for (Map<String, Object> row : rows) {
            UUID rowId = (UUID) row.get("id");
            UUID problemId = (UUID) row.get("problem_id");
            boolean primary = Boolean.TRUE.equals(row.get("is_primary"));
            List<UUID> targetRows = jdbc.queryForList(
                    "select id from problem_algorithm where problem_id = ? and category_id = ?", UUID.class,
                    problemId, targetId);
            if (targetRows.isEmpty()) {
                jdbc.update("update problem_algorithm set category_id = ? where id = ?", targetId, rowId);
                continue;
            }
            UUID targetRowId = targetRows.get(0);
            jdbc.update("update problem_algorithm set active = false, is_primary = false, primary_key = null where id = ?",
                    rowId);
            if (primary) {
                jdbc.update("update problem_algorithm set active = true, is_primary = true, primary_key = ? where id = ?",
                        problemId, targetRowId);
            }
        }
    }

    private void migrateAttributes(UUID sourceId, UUID targetId) {
        for (CategoryAttribute attribute : attributes.findAllByCategoryIdOrderBySortOrderAscCodeAsc(sourceId)) {
            attributes.findByCategoryIdAndCode(targetId, attribute.getCode()).ifPresentOrElse(
                    duplicate -> {
                        attribute.setActive(false);
                        attributes.save(attribute);
                    },
                    () -> {
                        attribute.setCategoryId(targetId);
                        attributes.save(attribute);
                    });
        }
    }

    private void migrateRelations(UUID sourceId, UUID targetId) {
        List<UUID> involved = relations.findAllByCategoryId(sourceId).stream().map(CategoryRelation::getPrerequisiteId).toList();
        List<UUID> dependents = relations.findAllByPrerequisiteId(sourceId).stream().map(CategoryRelation::getCategoryId).toList();
        relations.deleteAll(relations.findAllByCategoryId(sourceId));
        relations.deleteAll(relations.findAllByPrerequisiteId(sourceId));
        for (UUID prerequisite : involved) {
            UUID mapped = prerequisite.equals(sourceId) ? targetId : prerequisite;
            addRelationRow(targetId, mapped, null);
        }
        for (UUID dependent : dependents) {
            UUID mapped = dependent.equals(sourceId) ? targetId : dependent;
            addRelationRow(mapped, targetId, null);
        }
    }

    private void addRelationRow(UUID categoryId, UUID prerequisiteId, String note) {
        if (categoryId.equals(prerequisiteId) || relations.existsByCategoryIdAndPrerequisiteId(categoryId, prerequisiteId)) {
            return;
        }
        CategoryRelation relation = new CategoryRelation();
        relation.setCategoryId(categoryId);
        relation.setPrerequisiteId(prerequisiteId);
        relation.setNote(note);
        relations.save(relation);
    }

    @Transactional
    public AttributeView putAttribute(UUID categoryId, AttributeCommand command) {
        scopeGuard.requireGlobal("category:write");
        AlgorithmCategory category = require(categoryId);
        if (command.code() == null || command.code().isBlank()) {
            throw ApiException.badRequest("VALIDATION_ERROR", "属性编码不能为空");
        }
        String code = command.code().trim().toUpperCase(Locale.ROOT);
        if (!code.matches("[A-Z0-9_]{2,64}")) {
            throw ApiException.badRequest("VALIDATION_ERROR", "属性编码只能包含大写字母、数字与下划线");
        }
        String valueType = command.valueType() == null ? "TEXT" : command.valueType().toUpperCase(Locale.ROOT);
        if (!VALUE_TYPES.contains(valueType)) {
            throw ApiException.badRequest("CATEGORY_ATTRIBUTE_TYPE_INVALID", "属性类型只能是 TEXT/NUMBER/BOOLEAN");
        }
        String name = command.name() == null || command.name().isBlank() ? code : command.name().trim();
        CategoryAttribute attribute = attributes.findByCategoryIdAndCode(categoryId, code).orElseGet(CategoryAttribute::new);
        boolean created = attribute.getId() == null;
        attribute.setCategoryId(categoryId);
        attribute.setCode(code);
        attribute.setName(name);
        attribute.setValueType(valueType);
        attribute.setRequired(command.required() != null && command.required());
        attribute.setSortOrder(command.sortOrder() == null ? 0 : command.sortOrder());
        attribute.setActive(true);
        attributes.save(attribute);
        auditService.record(created ? "category.attribute_create" : "category.attribute_update",
                "category_attribute", attribute.getId(), null, snapshot(attribute), null);
        return toAttributeView(attribute);
    }

    @Transactional
    public List<RelationView> addRelation(UUID categoryId, UUID prerequisiteId, String note) {
        scopeGuard.requireGlobal("category:write");
        if (categoryId.equals(prerequisiteId)) {
            throw ApiException.badRequest("CATEGORY_RELATION_SELF", "不能把分类设为自身的前置");
        }
        AlgorithmCategory category = require(categoryId);
        AlgorithmCategory prerequisite = require(prerequisiteId);
        if (!category.isActive() || !prerequisite.isActive()) {
            throw ApiException.conflict("CATEGORY_INACTIVE", "停用的分类不能维护前置关系");
        }
        if (relations.existsByCategoryIdAndPrerequisiteId(categoryId, prerequisiteId)) {
            throw ApiException.conflict("CATEGORY_RELATION_EXISTS", "前置关系已存在");
        }
        if (reaches(prerequisiteId, categoryId)) {
            throw ApiException.conflict("CATEGORY_RELATION_CYCLE", "前置关系会形成环，已拒绝");
        }
        CategoryRelation relation = new CategoryRelation();
        relation.setCategoryId(categoryId);
        relation.setPrerequisiteId(prerequisiteId);
        relation.setNote(trimToNull(note));
        relations.save(relation);
        auditService.record("category.relation_add", "category_relation", relation.getId(), null,
                Map.of("categoryId", categoryId.toString(), "prerequisiteId", prerequisiteId.toString()), null);
        return relations.findAllByCategoryId(categoryId).stream().map(this::toRelationView).toList();
    }

    @Transactional
    public List<RelationView> removeRelation(UUID categoryId, UUID prerequisiteId) {
        scopeGuard.requireGlobal("category:write");
        AlgorithmCategory category = require(categoryId);
        relations.findAllByCategoryId(categoryId).stream()
                .filter(relation -> relation.getPrerequisiteId().equals(prerequisiteId))
                .findFirst()
                .ifPresentOrElse(relation -> {
                    relations.delete(relation);
                    auditService.record("category.relation_remove", "category_relation", relation.getId(),
                            Map.of("categoryId", categoryId.toString(), "prerequisiteId", prerequisiteId.toString()),
                            null, null);
                }, () -> {
                    throw ApiException.notFound("前置关系不存在");
                });
        return relations.findAllByCategoryId(category.getId()).stream().map(this::toRelationView).toList();
    }

    // ------------------------------------------------------------------ 内部

    private boolean reaches(UUID from, UUID target) {
        Set<UUID> visited = new HashSet<>();
        Deque<UUID> stack = new ArrayDeque<>();
        stack.push(from);
        while (!stack.isEmpty()) {
            UUID current = stack.pop();
            if (!visited.add(current)) {
                continue;
            }
            for (CategoryRelation relation : relations.findAllByCategoryId(current)) {
                UUID next = relation.getPrerequisiteId();
                if (next.equals(target)) {
                    return true;
                }
                stack.push(next);
            }
        }
        return false;
    }

    private AlgorithmCategory require(UUID id) {
        return categories.findById(id).orElseThrow(() -> ApiException.notFound("算法分类不存在"));
    }

    private int depthOf(AlgorithmCategory category) {
        int depth = 0;
        AlgorithmCategory current = category;
        while (current != null && depth < MAX_DEPTH + 1) {
            depth++;
            current = current.getParentId() == null ? null : categories.findById(current.getParentId()).orElse(null);
        }
        return depth;
    }

    private UUID requirePerson(UUID personId) {
        if (personId == null) {
            return null;
        }
        if (!persons.existsById(personId)) {
            throw ApiException.badRequest("PERSON_NOT_FOUND", "负责人不存在");
        }
        return personId;
    }

    private String requireName(String name) {
        String value = name == null ? "" : name.trim();
        if (value.isEmpty() || value.length() > 64) {
            throw ApiException.badRequest("VALIDATION_ERROR", "分类名称必填且不超过 64 字");
        }
        return value;
    }

    private String nameKey(UUID parentId, String name) {
        return (parentId == null ? "root" : parentId.toString()) + "|" + name.toLowerCase(Locale.ROOT);
    }

    private String normalizeCode(String code, String name) {
        String value = code == null || code.isBlank() ? "CAT_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT) : code.trim();
        if (!value.matches("[A-Za-z0-9_]{2,64}")) {
            throw ApiException.badRequest("VALIDATION_ERROR", "分类编码只能包含字母、数字与下划线：" + name);
        }
        return value.toUpperCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private CategoryView toView(AlgorithmCategory category) {
        List<AttributeView> attributeViews = attributes.findAllByCategoryIdOrderBySortOrderAscCodeAsc(category.getId())
                .stream().map(this::toAttributeView).toList();
        List<RelationView> relationViews = relations.findAllByCategoryId(category.getId())
                .stream().map(this::toRelationView).toList();
        return new CategoryView(category.getId(), category.getCode(), category.getName(), category.getShortName(),
                category.getDescription(), category.getParentId(), category.getStage(), category.getSuggestedDifficulty(),
                category.getOwnerPersonId(), category.getSortOrder(), category.isActive(), category.getMergedIntoId(),
                attributeViews, relationViews);
    }

    private CategoryNode toNode(AlgorithmCategory category, Map<UUID, List<AlgorithmCategory>> byParent, int depth) {
        List<CategoryNode> children = depth >= MAX_DEPTH ? List.of()
                : byParent.getOrDefault(category.getId(), List.of()).stream()
                        .map(child -> toNode(child, byParent, depth + 1)).toList();
        return new CategoryNode(toView(category), children);
    }

    private AttributeView toAttributeView(CategoryAttribute attribute) {
        return new AttributeView(attribute.getId(), attribute.getCode(), attribute.getName(), attribute.getValueType(),
                attribute.isRequired(), attribute.getSortOrder(), attribute.isActive());
    }

    private RelationView toRelationView(CategoryRelation relation) {
        String prerequisiteName = categories.findById(relation.getPrerequisiteId())
                .map(AlgorithmCategory::getName).orElse(null);
        return new RelationView(relation.getId(), relation.getPrerequisiteId(), prerequisiteName, relation.getNote());
    }

    private Map<String, Object> snapshot(AlgorithmCategory category) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("code", category.getCode());
        map.put("name", category.getName());
        map.put("parentId", category.getParentId());
        map.put("stage", category.getStage());
        map.put("sortOrder", category.getSortOrder());
        map.put("active", category.isActive());
        map.put("mergedIntoId", category.getMergedIntoId());
        return map;
    }

    private Map<String, Object> snapshot(CategoryAttribute attribute) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("categoryId", attribute.getCategoryId());
        map.put("code", attribute.getCode());
        map.put("name", attribute.getName());
        map.put("valueType", attribute.getValueType());
        map.put("required", attribute.isRequired());
        map.put("active", attribute.isActive());
        return map;
    }

    // ------------------------------------------------------------------ 视图与命令

    public record CreateCommand(String code, String name, UUID parentId, String shortName, String description,
                                String suggestedDifficulty, UUID ownerPersonId, Integer sortOrder) {
    }

    public record UpdateCommand(String name, String shortName, String description, String suggestedDifficulty,
                                UUID ownerPersonId, Integer sortOrder, String reason) {
    }

    public record AttributeCommand(String code, String name, String valueType, Boolean required, Integer sortOrder) {
    }

    public record AttributeView(UUID id, String code, String name, String valueType, boolean required,
                                int sortOrder, boolean active) {
    }

    public record RelationView(UUID id, UUID prerequisiteId, String prerequisiteName, String note) {
    }

    public record CategoryView(UUID id, String code, String name, String shortName, String description,
                               UUID parentId, Integer stage, String suggestedDifficulty, UUID ownerPersonId,
                               int sortOrder, boolean active, UUID mergedIntoId,
                               List<AttributeView> attributes, List<RelationView> relations) {
    }

    public record CategoryNode(CategoryView category, List<CategoryNode> children) {
    }
}