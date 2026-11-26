package cn.edu.scuec.icpc.lab.member;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/semester-registrations")
public class RegistrationController {

    private final RegistrationService registrationService;

    public RegistrationController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('member:read')")
    public List<RegistrationService.View> list(@RequestParam UUID semesterId,
                                               @RequestParam(required = false) String assessment) {
        return registrationService.list(semesterId, assessment);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('profile:read:self')")
    public RegistrationService.View register(@RequestBody RegisterRequest request) {
        return registrationService.register(request.semesterId());
    }

    @PostMapping("/{id}/decline")
    @PreAuthorize("hasAuthority('profile:read:self')")
    public RegistrationService.View decline(@PathVariable UUID id) {
        return registrationService.decline(id);
    }

    @PostMapping("/{id}/assessment")
    @PreAuthorize("hasAuthority('member:assess')")
    public RegistrationService.View assess(@PathVariable UUID id, @RequestBody AssessRequest request) {
        return registrationService.assess(id, request.assessment(), request.note());
    }

    public record RegisterRequest(UUID semesterId) {
    }

    public record AssessRequest(String assessment, String note) {
    }
}