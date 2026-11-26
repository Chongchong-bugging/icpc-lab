package cn.edu.scuec.icpc.lab.member;

import cn.edu.scuec.icpc.lab.audit.AuditService;
import cn.edu.scuec.icpc.lab.common.ApiException;
import cn.edu.scuec.icpc.lab.notification.NotificationService;
import cn.edu.scuec.icpc.lab.org.Semester;
import cn.edu.scuec.icpc.lab.org.SemesterRepository;
import cn.edu.scuec.icpc.lab.security.CurrentUser;
import cn.edu.scuec.icpc.lab.security.ScopeGuard;
import jakarta.transaction.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 学期注册与考核（FR-04）。注册是本人动作，考核结论由有权限者写入。 */
@Service
public class RegistrationService {

    private final SemesterRegistrationRepository repository;
    private final SemesterRepository semesterRepository;
    private final MembershipRepository membershipRepository;
    private final CurrentUser currentUser;
    private final ScopeGuard scopeGuard;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final JdbcTemplate jdbc;

    public RegistrationService(SemesterRegistrationRepository repository, SemesterRepository semesterRepository,
                               MembershipRepository membershipRepository, CurrentUser currentUser,
                               ScopeGuard scopeGuard, AuditService auditService,
                               NotificationService notificationService, JdbcTemplate jdbc) {
        this.repository = repository;
        this.semesterRepository = semesterRepository;
        this.membershipRepository = membershipRepository;
        this.currentUser = currentUser;
        this.scopeGuard = scopeGuard;
        this.auditService = auditService;
        this.notificationService = notificationService;
        this.jdbc = jdbc;
    }

    @Transactional
    public View register(UUID semesterId) {
        UUID personId = currentUser.principal().personId();
        if (membershipRepository.findByActiveKey(personId.toString()).isEmpty()) {
            throw ApiException.conflict("NOT_MEMBER", "只有在册会员可以学期注册");
        }
        Semester semester = semesterRepository.findById(semesterId)
                .orElseThrow(() -> ApiException.notFound("学期不存在"));
        if (!semester.isActive()) {
            throw ApiException.conflict("SEMESTER_INACTIVE", "该学期未开放注册");
        }
        SemesterRegistration registration = repository.findByPersonIdAndSemesterId(personId, semesterId)
                .orElseGet(() -> {
                    SemesterRegistration created = new SemesterRegistration();
                    created.setPersonId(personId);
                    created.setSemesterId(semesterId);
                    return created;
                });
        registration.setStatus(SemesterRegistration.REGISTERED);
        registration.setAssessment(SemesterRegistration.ASSESS_PENDING);
        repository.save(registration);
        auditService.record("registration.register", "semester_registration", registration.getId(), null,
                Map.of("semesterId", semesterId, "status", registration.getStatus()), null);
        return view(registration);
    }

    @Transactional
    public View decline(UUID id) {
        SemesterRegistration registration = requireOwned(id);
        registration.setStatus(SemesterRegistration.DECLINED);
        repository.save(registration);
        auditService.record("registration.decline", "semester_registration", registration.getId(),
                Map.of("status", SemesterRegistration.REGISTERED),
                Map.of("status", SemesterRegistration.DECLINED), null);
        return view(registration);
    }

    /** 考核结论：只记录，不自动除名/处罚（DOM-12 / DENY-04）。 */
    @Transactional
    public View assess(UUID id, String assessment, String note) {
        if (!SemesterRegistration.ASSESS_PENDING.equals(assessment)
                && !SemesterRegistration.QUALIFIED.equals(assessment)
                && !SemesterRegistration.OBSERVING.equals(assessment)) {
            throw ApiException.badRequest("INVALID_ASSESSMENT", "考核结论只能是 PENDING/QUALIFIED/OBSERVING");
        }
        SemesterRegistration registration = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("注册记录不存在"));
        scopeGuard.assertCanAccessPerson(registration.getPersonId(), "member:assess");
        String from = registration.getAssessment();
        registration.setAssessment(assessment);
        registration.setNote(note);
        registration.setAssessedBy(currentUser.principal().personId());
        registration.setAssessedAt(OffsetDateTime.now());
        repository.save(registration);
        auditService.record("registration.assess", "semester_registration", registration.getId(),
                Map.of("assessment", from), Map.of("assessment", assessment), note);
        // 考核维度是会员身份的单一事实来源，学期登记结论同步回 membership（ADR-0002）
        membershipRepository.findByActiveKey(registration.getPersonId().toString()).ifPresent(membership -> {
            String before = membership.getAssessment();
            if (!assessment.equals(before)) {
                membership.setAssessment(assessment);
                membershipRepository.save(membership);
                auditService.record("member.assessment", "membership", membership.getId(),
                        Map.of("assessment", before), Map.of("assessment", assessment), note);
            }
        });
        notificationService.notify(registration.getPersonId(), "SEMESTER_ASSESSMENT", "学期考核结果",
                "你的学期考核结论为 " + assessment, "SEMESTER_REGISTRATION", registration.getId());
        return view(registration);
    }

    public List<View> list(UUID semesterId, String assessment) {
        scopeGuard.requireAnyScope("member:read");
        List<SemesterRegistration> rows = assessment == null || assessment.isBlank()
                ? repository.findBySemesterIdOrderByCreatedAtAsc(semesterId)
                : repository.findBySemesterIdAndAssessment(semesterId, assessment);
        return rows.stream()
                .filter(row -> scopeGuard.canAccessPerson(row.getPersonId(), "member:read"))
                .map(this::view).toList();
    }

    private SemesterRegistration requireOwned(UUID id) {
        SemesterRegistration registration = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("注册记录不存在"));
        if (!registration.getPersonId().equals(currentUser.principal().personId())) {
            throw ApiException.forbidden("FORBIDDEN", "只能操作本人注册记录");
        }
        return registration;
    }

    private View view(SemesterRegistration registration) {
        List<String> names = jdbc.queryForList("select name from person where id = ?", String.class,
                registration.getPersonId());
        String personName = names.isEmpty() ? null : names.get(0);
        return new View(registration.getId(), registration.getPersonId(), personName, registration.getSemesterId(),
                registration.getStatus(), registration.getAssessment(), registration.getNote(),
                registration.getAssessedAt());
    }

    public record View(UUID id, UUID personId, String personName, UUID semesterId, String status,
                       String assessment, String note, OffsetDateTime assessedAt) {
    }
}