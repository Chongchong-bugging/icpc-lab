package cn.edu.scuec.icpc.lab.problem;

import cn.edu.scuec.icpc.lab.common.PageResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * 题目接口（FR-06）。读 problem:read，写 problem:write（权限矩阵 §3）。
 */
@RestController
@RequestMapping("/api/v1/problems")
public class ProblemController {

    private final ProblemService problemService;

    public ProblemController(ProblemService problemService) {
        this.problemService = problemService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('problem:read')")
    public PageResponse<ProblemService.ProblemView> list(
            @RequestParam(required = false) String source,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) UUID tagId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return problemService.list(source, categoryId, tagId, keyword, active, page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('problem:read')")
    public ProblemService.ProblemView get(@PathVariable UUID id) {
        return problemService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('problem:write')")
    public ProblemService.ProblemView create(@RequestBody ProblemService.SaveCommand command) {
        return problemService.create(command);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('problem:write')")
    public ProblemService.ProblemView update(@PathVariable UUID id,
                                             @RequestBody ProblemService.SaveCommand command) {
        return problemService.update(id, command);
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasAuthority('problem:write')")
    public ProblemService.ProblemView deactivate(@PathVariable UUID id,
                                                 @RequestBody(required = false) DeactivateRequest request) {
        return problemService.deactivate(id, request == null ? null : request.reason());
    }

    public record DeactivateRequest(String reason) {
    }
}