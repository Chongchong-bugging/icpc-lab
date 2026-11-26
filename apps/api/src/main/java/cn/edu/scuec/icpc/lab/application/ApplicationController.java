package cn.edu.scuec.icpc.lab.application;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/applications")
public class ApplicationController {

    private final ApplicationService applicationService;

    public ApplicationController(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @GetMapping("/mine")
    @PreAuthorize("hasAuthority('application:read:self')")
    public List<ApplicationService.ApplicationView> mine() {
        return applicationService.mine();
    }

    @GetMapping("/pending")
    @PreAuthorize("hasAuthority('application:review')")
    public List<ApplicationService.ApplicationView> pending() {
        return applicationService.pending();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('application:read:self')")
    public ApplicationService.ApplicationView detail(@PathVariable UUID id) {
        return applicationService.detail(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('application:create')")
    public ApplicationService.ApplicationView create(@RequestBody CreateRequest request) {
        return applicationService.create(request.type(), request.channel(), request.form(), request.reason());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('application:create')")
    public ApplicationService.ApplicationView update(@PathVariable UUID id, @RequestBody UpdateRequest request) {
        return applicationService.update(id, request.form(), request.changeSummary());
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAuthority('application:create')")
    public ApplicationService.ApplicationView submit(@PathVariable UUID id) {
        return applicationService.submit(id);
    }

    @PostMapping("/{id}/withdraw")
    @PreAuthorize("hasAuthority('application:withdraw')")
    public ApplicationService.ApplicationView withdraw(@PathVariable UUID id,
                                                       @RequestBody(required = false) ReasonRequest request) {
        return applicationService.withdraw(id, request == null ? null : request.reason());
    }

    @PostMapping("/{id}/review")
    @PreAuthorize("hasAuthority('application:review')")
    public ApplicationService.ApplicationView review(@PathVariable UUID id, @RequestBody ReviewRequest request) {
        return applicationService.review(id, request.decision(), request.comment(), request.reason());
    }

    @PostMapping("/{id}/effect")
    @PreAuthorize("hasAuthority('member:write')")
    public ApplicationService.ApplicationView effect(@PathVariable UUID id,
                                                     @RequestBody(required = false) ReasonRequest request) {
        return applicationService.effect(id, request == null ? null : request.reason());
    }

    public record CreateRequest(String type, String channel, String form, String reason) {
    }

    public record UpdateRequest(String form, String changeSummary) {
    }

    public record ReviewRequest(String decision, String comment, String reason) {
    }

    public record ReasonRequest(String reason) {
    }
}