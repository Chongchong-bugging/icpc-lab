package cn.edu.scuec.icpc.lab.member;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/members")
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @GetMapping("/me")
    @PreAuthorize("hasAuthority('profile:read:self')")
    public MemberService.MemberProfile me() {
        return memberService.me();
    }

    @GetMapping
    @PreAuthorize("hasAuthority('member:read')")
    public List<MemberService.MemberSummary> directory(@RequestParam(required = false) String keyword,
                                                       @RequestParam(required = false) String lifecycle,
                                                       @RequestParam(required = false) String type,
                                                       @RequestParam(required = false) UUID departmentId) {
        return memberService.directory(keyword, lifecycle, type, departmentId);
    }

    @GetMapping("/{personId}")
    @PreAuthorize("hasAuthority('member:read')")
    public MemberService.MemberProfile profile(@PathVariable UUID personId) {
        return memberService.profile(personId, "member:read");
    }

    @PostMapping("/{personId}/lifecycle")
    @PreAuthorize("hasAuthority('member:write')")
    public MemberService.MemberProfile changeLifecycle(@PathVariable UUID personId,
                                                       @RequestBody LifecycleRequest request) {
        return memberService.changeLifecycle(personId, request.lifecycle(), request.reason());
    }

    @PostMapping("/{personId}/assessment")
    @PreAuthorize("hasAuthority('member:assess')")
    public MemberService.MemberProfile assess(@PathVariable UUID personId, @RequestBody AssessRequest request) {
        return memberService.assess(personId, request.semesterId(), request.assessment(), request.note());
    }

    @GetMapping("/{personId}/disciplines")
    @PreAuthorize("hasAuthority('member:read')")
    public List<DisciplineRecord> disciplines(@PathVariable UUID personId) {
        return memberService.disciplines(personId);
    }

    @PostMapping("/{personId}/disciplines")
    @PreAuthorize("hasAuthority('member:discipline')")
    public DisciplineRecord discipline(@PathVariable UUID personId, @RequestBody DisciplineRequest request) {
        return memberService.discipline(personId, request.kind(), request.title(), request.detail(),
                request.decidedAt());
    }

    public record LifecycleRequest(String lifecycle, String reason) {
    }

    public record AssessRequest(UUID semesterId, String assessment, String note) {
    }

    public record DisciplineRequest(String kind, String title, String detail, OffsetDateTime decidedAt) {
    }
}