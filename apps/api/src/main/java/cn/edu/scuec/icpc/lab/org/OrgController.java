package cn.edu.scuec.icpc.lab.org;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/org")
public class OrgController {

    private final OrgService orgService;

    public OrgController(OrgService orgService) {
        this.orgService = orgService;
    }

    @GetMapping("/departments")
    @PreAuthorize("hasAuthority('org:read')")
    public List<OrgService.DepartmentView> departments() {
        return orgService.departments();
    }

    @PostMapping("/departments")
    @PreAuthorize("hasAuthority('org:write')")
    public OrgService.DepartmentView createDepartment(@RequestBody OrgService.CreateDepartment request) {
        return orgService.createDepartment(request);
    }

    @GetMapping("/positions")
    @PreAuthorize("hasAuthority('org:read')")
    public List<OrgService.PositionView> positions() {
        return orgService.positions();
    }

    @PostMapping("/positions")
    @PreAuthorize("hasAuthority('org:write')")
    public OrgService.PositionView createPosition(@RequestBody OrgService.CreatePosition request) {
        return orgService.createPosition(request);
    }

    @GetMapping("/terms")
    @PreAuthorize("hasAuthority('org:read')")
    public List<OrgService.TermView> terms() {
        return orgService.terms();
    }

    @PostMapping("/terms")
    @PreAuthorize("hasAuthority('org:write')")
    public OrgService.TermView createTerm(@RequestBody OrgService.CreateTerm request) {
        return orgService.createTerm(request);
    }

    @GetMapping("/semesters")
    @PreAuthorize("hasAuthority('org:read')")
    public List<OrgService.SemesterView> semesters() {
        return orgService.semesters();
    }

    @PostMapping("/semesters")
    @PreAuthorize("hasAuthority('org:write')")
    public OrgService.SemesterView createSemester(@RequestBody OrgService.CreateSemester request) {
        return orgService.createSemester(request);
    }

    @GetMapping("/position-assignments")
    @PreAuthorize("hasAuthority('org:read')")
    public List<OrgService.AssignmentView> assignments(@RequestParam(required = false) UUID personId) {
        return orgService.assignments(personId);
    }

    @PostMapping("/position-assignments")
    @PreAuthorize("hasAuthority('position:assign')")
    public OrgService.AssignmentView assign(@RequestBody OrgService.AssignRequest request) {
        return orgService.assign(request);
    }

    @PostMapping("/position-assignments/{id}/end")
    @PreAuthorize("hasAuthority('position:assign')")
    public OrgService.AssignmentView end(@PathVariable UUID id, @RequestBody(required = false) EndRequest request) {
        return orgService.end(id, request == null ? null : request.reason());
    }

    public record EndRequest(String reason) {
    }
}