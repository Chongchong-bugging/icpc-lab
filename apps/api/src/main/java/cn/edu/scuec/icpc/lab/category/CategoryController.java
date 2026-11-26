package cn.edu.scuec.icpc.lab.category;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * 算法目录接口（FR-37）。读取复用 problem:read，维护复用 category:write（权限矩阵 §3）。
 */
@RestController
@RequestMapping("/api/v1/algorithm-categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('problem:read')")
    public Object list(@RequestParam(required = false) UUID parentId,
                       @RequestParam(defaultValue = "false") boolean tree,
                       @RequestParam(defaultValue = "false") boolean includeInactive) {
        if (tree) {
            return categoryService.tree(parentId, includeInactive);
        }
        return categoryService.list(parentId, includeInactive);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('problem:read')")
    public CategoryService.CategoryView get(@PathVariable UUID id) {
        return categoryService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('category:write')")
    public CategoryService.CategoryView create(@RequestBody CategoryService.CreateCommand command) {
        return categoryService.create(command);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('category:write')")
    public CategoryService.CategoryView update(@PathVariable UUID id,
                                               @RequestBody CategoryService.UpdateCommand command) {
        return categoryService.update(id, command);
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasAuthority('category:write')")
    public CategoryService.CategoryView deactivate(@PathVariable UUID id,
                                                   @RequestBody(required = false) ReasonRequest request) {
        return categoryService.deactivate(id, request == null ? null : request.reason());
    }

    @PostMapping("/{id}/merge")
    @PreAuthorize("hasAuthority('category:write')")
    public CategoryService.CategoryView merge(@PathVariable UUID id, @RequestBody MergeRequest request) {
        return categoryService.merge(id, request.targetId(), request.reason());
    }

    @PostMapping("/{id}/attributes")
    @PreAuthorize("hasAuthority('category:write')")
    public CategoryService.AttributeView putAttribute(@PathVariable UUID id,
                                                      @RequestBody CategoryService.AttributeCommand command) {
        return categoryService.putAttribute(id, command);
    }

    @PostMapping("/{id}/relations")
    @PreAuthorize("hasAuthority('category:write')")
    public List<CategoryService.RelationView> addRelation(@PathVariable UUID id, @RequestBody RelationRequest request) {
        return categoryService.addRelation(id, request.prerequisiteId(), request.note());
    }

    @DeleteMapping("/{id}/relations/{prerequisiteId}")
    @PreAuthorize("hasAuthority('category:write')")
    public List<CategoryService.RelationView> removeRelation(@PathVariable UUID id, @PathVariable UUID prerequisiteId) {
        return categoryService.removeRelation(id, prerequisiteId);
    }

    public record MergeRequest(UUID targetId, String reason) {
    }

    public record RelationRequest(UUID prerequisiteId, String note) {
    }

    public record ReasonRequest(String reason) {
    }
}