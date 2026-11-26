package cn.edu.scuec.icpc.lab.trainingplan;

import cn.edu.scuec.icpc.lab.common.PageResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * 训练计划接口（WP-P0-14）：草稿 → 提交 → 审批 → 发布 → 修订，历史版本可查。
 */
@RestController
@RequestMapping("/api/v1/training-plans")
public class TrainingPlanController {

    private final TrainingPlanService planService;

    public TrainingPlanController(TrainingPlanService planService) {
        this.planService = planService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('plan:read')")
    public PageResponse<TrainingPlanService.PlanView> list(
            @RequestParam(required = false) UUID termId,
            @RequestParam(required = false) UUID semesterId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return planService.list(termId, semesterId, status, page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('plan:read')")
    public TrainingPlanService.PlanView get(@PathVariable UUID id) {
        return planService.get(id);
    }

    @GetMapping("/{id}/versions")
    @PreAuthorize("hasAuthority('plan:read')")
    public List<TrainingPlanService.VersionView> versions(@PathVariable UUID id) {
        return planService.versions(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('plan:write')")
    public TrainingPlanService.PlanView create(@RequestBody TrainingPlanService.CreateCommand command) {
        return planService.create(command);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('plan:write')")
    public TrainingPlanService.PlanView update(@PathVariable UUID id,
                                               @RequestBody TrainingPlanService.UpdateCommand command) {
        return planService.update(id, command);
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAuthority('plan:write')")
    public TrainingPlanService.PlanView submit(@PathVariable UUID id,
                                               @RequestBody(required = false) ReasonRequest request) {
        return planService.submit(id, request == null ? null : request.reason());
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('plan:approve')")
    public TrainingPlanService.PlanView approve(@PathVariable UUID id, @RequestBody DecisionRequest request) {
        return planService.approve(id, request.decision(), request.comment());
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAuthority('plan:publish')")
    public TrainingPlanService.PlanView publish(@PathVariable UUID id,
                                                @RequestBody(required = false) ReasonRequest request) {
        return planService.publish(id, request == null ? null : request.reason());
    }

    @PostMapping("/{id}/versions")
    @PreAuthorize("hasAuthority('plan:write')")
    public TrainingPlanService.PlanView revise(@PathVariable UUID id,
                                               @RequestBody(required = false) RevisionRequest request) {
        return planService.revise(id, request == null ? null : request.changeSummary());
    }

    public record ReasonRequest(String reason) {
    }

    public record DecisionRequest(String decision, String comment) {
    }

    public record RevisionRequest(String changeSummary) {
    }
}