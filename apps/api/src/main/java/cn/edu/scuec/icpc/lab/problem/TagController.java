package cn.edu.scuec.icpc.lab.problem;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * 标签字典接口（DOM-19）。读取复用 problem:read，维护复用 category:write。
 */
@RestController
@RequestMapping("/api/v1/tags")
public class TagController {

    private final ProblemService problemService;

    public TagController(ProblemService problemService) {
        this.problemService = problemService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('problem:read')")
    public List<ProblemService.TagView> list(@RequestParam(required = false) String type) {
        return problemService.listTags(type);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('category:write')")
    public ProblemService.TagView create(@RequestBody ProblemService.TagCommand command) {
        return problemService.createTag(command);
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasAuthority('category:write')")
    public ProblemService.TagView deactivate(@PathVariable UUID id) {
        return problemService.deactivateTag(id);
    }
}