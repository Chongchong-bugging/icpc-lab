package cn.edu.scuec.icpc.lab.member;

import cn.edu.scuec.icpc.lab.audit.AuditService;
import cn.edu.scuec.icpc.lab.common.ApiException;
import cn.edu.scuec.icpc.lab.identity.Person;
import cn.edu.scuec.icpc.lab.identity.PersonRepository;
import cn.edu.scuec.icpc.lab.notification.NotificationService;
import cn.edu.scuec.icpc.lab.security.AccountPrincipal;
import cn.edu.scuec.icpc.lab.security.CurrentUser;
import cn.edu.scuec.icpc.lab.security.ScopeGrant;
import cn.edu.scuec.icpc.lab.security.ScopeGuard;
import cn.edu.scuec.icpc.lab.security.ScopeType;
import jakarta.transaction.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 会员档案、分类筛选、身份状态与奖惩（FR-01/02/03/05，DOM-02/12/15）。 */
@Service
public class MemberService {

    private final PersonRepository personRepository;
    private final MembershipRepository membershipRepository;
    private final IdentityHistoryRepository historyRepository;
    private final DisciplineRecordRepository disciplineRepository;
    private final SemesterRegistrationRepository registrationRepository;
    private final JdbcTemplate jdbc;
    private final CurrentUser currentUser;
    private final ScopeGuard scopeGuard;
    private final AuditService auditService;
    private final NotificationService notificationService;

    public MemberService(PersonRepository personRepository, MembershipRepository membershipRepository,
                         IdentityHistoryRepository historyRepository, DisciplineRecordRepository disciplineRepository,
                         SemesterRegistrationRepository registrationRepository, JdbcTemplate jdbc, CurrentUser currentUser,
                         ScopeGuard scopeGuard, AuditService auditService, NotificationService notificationService) {
        this.personRepository = personRepository;
        this.membershipRepository = membershipRepository;
        this.historyRepository = historyRepository;
        this.disciplineRepository = disciplineRepository;
        this.registrationRepository = registrationRepository;
        this.jdbc = jdbc;
        this.currentUser = currentUser;
        this.scopeGuard = scopeGuard;
        this.auditService = auditService;
        this.notificationService = notificationService;
    }

    /** 本人档案，SELF 范围即可（FR-01）。 */
    public MemberProfile me() {
        return profile(currentUser.principal().personId(), "profile:read:self");
    }

    public MemberProfile profile(UUID personId, String permission) {
        Person person = personRepository.findById(personId)
                .orElseThrow(() -> ApiException.notFound("人员不存在"));
        if (!personId.equals(currentUser.principal().personId())) {
            scopeGuard.assertCanAccessPerson(personId, permission);
        }
        // 在册记录优先；退出/除名后仍可查看本人最近一条历史身份（终态保留，DOM-15）
        Membership membership = membershipRepository.findByActiveKey(personId.toString())
                .orElseGet(() -> membershipRepository.findByPersonIdOrderByJoinedAtDesc(personId).stream()
                        .findFirst().orElse(null));
        List<PositionView> positions = jdbc.query("""
                select pa.id, p.name as position_name, d.name as department_name, pa.start_date, pa.end_date,
                       pa.status, pa.term_id
                  from position_assignment pa
                  join org_position p on p.id = pa.position_id
                  left join department d on d.id = pa.department_id
                 where pa.person_id = ?
                 order by pa.start_date desc
                """, (rs, i) -> new PositionView((UUID) rs.getObject("id"), rs.getString("position_name"),
                rs.getString("department_name"), rs.getObject("start_date", OffsetDateTime.class),
                rs.getObject("end_date", OffsetDateTime.class), rs.getString("status")), personId);
        String departmentName = null;
        if (person.getDepartmentId() != null) {
            List<String> names = jdbc.queryForList("select name from department where id = ?", String.class,
                    person.getDepartmentId());
            departmentName = names.isEmpty() ? null : names.get(0);
        }
        return new MemberProfile(person.getId(), person.getStudentNo(), person.getName(), person.getGender(),
                person.getDepartmentId(), departmentName, person.getMajor(), person.getGrade(),
                person.getEnrollmentYear(), person.getEmail(), person.getPhone(),
                membership == null ? null : new MembershipView(membership.getType(), membership.getLifecycle(),
                        membership.getAssessment(), membership.getJoinedAt(), membership.getExitedAt(),
                        membership.getExitReason()),
                positions);
    }

    /** 分类查询：按姓名/学号/生命周期/类型/部门，数据范围在查询层注入（TECH-11）。 */
    public List<MemberSummary> directory(String keyword, String lifecycle, String type, UUID departmentId) {
        AccountPrincipal principal = currentUser.principal();
        scopeGuard.requireAnyScope("member:read");
        StringBuilder sql = new StringBuilder("""
                select p.id, p.student_no, p.name, p.department_id, d.name as department_name,
                       p.major, p.grade, m.type, m.lifecycle, m.assessment, m.joined_at
                  from person p
                  join membership m on m.person_id = p.id and m.active_key is not null
                  left join department d on d.id = p.department_id
                 where 1 = 1
                """);
        List<Object> args = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" and (lower(p.name) like ? or p.student_no like ?)");
            args.add("%" + keyword.toLowerCase() + "%");
            args.add("%" + keyword + "%");
        }
        if (lifecycle != null && !lifecycle.isBlank()) {
            sql.append(" and m.lifecycle = ?");
            args.add(lifecycle);
        }
        if (type != null && !type.isBlank()) {
            sql.append(" and m.type = ?");
            args.add(type);
        }
        if (departmentId != null) {
            sql.append(" and p.department_id = ?");
            args.add(departmentId);
        }
        List<ScopeGrant> grants = principal.grants().stream()
                .filter(grant -> grant.permission().equals("member:read"))
                .toList();
        boolean global = grants.stream().anyMatch(grant -> grant.scopeType() == ScopeType.GLOBAL);
        if (!global) {
            List<UUID> departmentIds = grants.stream()
                    .filter(grant -> grant.scopeType() == ScopeType.DEPARTMENT && grant.scopeId() != null)
                    .map(ScopeGrant::scopeId)
                    .toList();
            List<String> conditions = new ArrayList<>();
            if (!departmentIds.isEmpty()) {
                conditions.add("p.department_id in (" + String.join(",", departmentIds.stream().map(x -> "?").toList()) + ")");
                args.addAll(departmentIds);
            }
            conditions.add("p.id = ?");
            args.add(principal.personId());
            sql.append(" and (").append(String.join(" or ", conditions)).append(")");
        }
        sql.append(" order by m.joined_at desc nulls last, p.name");
        return jdbc.query(sql.toString(), (rs, i) -> new MemberSummary((UUID) rs.getObject("id"),
                rs.getString("student_no"), rs.getString("name"), (UUID) rs.getObject("department_id"),
                rs.getString("department_name"), rs.getString("major"), rs.getObject("grade", Integer.class),
                rs.getString("type"), rs.getString("lifecycle"), rs.getString("assessment"),
                rs.getObject("joined_at", OffsetDateTime.class)), args.toArray());
    }

    /** 试用转正等身份生命周期变更；退出/除名必须走退出申请，保证审批轨迹（DOM-07/DOM-12）。 */
    @Transactional
    public MemberProfile changeLifecycle(UUID personId, String lifecycle, String reason) {
        if (!Membership.PROBATION.equals(lifecycle) && !Membership.FULL.equals(lifecycle)) {
            throw ApiException.badRequest("LIFECYCLE_MANUAL_ONLY",
                    "退出/除名只能经退出申请与审批生效（DOM-07/DOM-12）");
        }
        scopeGuard.assertCanAccessPerson(personId, "member:write");
        Membership membership = membershipRepository.findByActiveKey(personId.toString())
                .orElseThrow(() -> ApiException.conflict("MEMBERSHIP_NOT_FOUND", "该人员没有在册会员记录"));
        if (membership.isTerminal()) {
            throw ApiException.conflict("MEMBERSHIP_TERMINAL", "终态会员不可复活（DOM-02）");
        }
        String from = membership.getLifecycle();
        if (from.equals(lifecycle)) {
            return profile(personId, "member:read");
        }
        membership.setLifecycle(lifecycle);
        membershipRepository.save(membership);
        history(personId, "LIFECYCLE", from, lifecycle, reason);
        auditService.record("member.lifecycle", "membership", membership.getId(),
                Map.of("lifecycle", from), Map.of("lifecycle", lifecycle), reason);
        notificationService.notify(personId, "MEMBER_LIFECYCLE", "会员状态变更",
                "你的会员状态已由 " + from + " 变更为 " + lifecycle, "MEMBERSHIP", membership.getId());
        return profile(personId, "member:read");
    }

    /** 学期考核：只写考核结论，不自动除名（DOM-12 / DENY-04）。 */
    @Transactional
    public MemberProfile assess(UUID personId, UUID semesterId, String assessment, String note) {
        if (!SemesterRegistration.ASSESS_PENDING.equals(assessment)
                && !SemesterRegistration.QUALIFIED.equals(assessment)
                && !SemesterRegistration.OBSERVING.equals(assessment)) {
            throw ApiException.badRequest("INVALID_ASSESSMENT", "考核结论只能是 PENDING/QUALIFIED/OBSERVING");
        }
        scopeGuard.assertCanAccessPerson(personId, "member:assess");
        Membership membership = membershipRepository.findByActiveKey(personId.toString())
                .orElseThrow(() -> ApiException.conflict("MEMBERSHIP_NOT_FOUND", "该人员没有在册会员记录"));
        SemesterRegistration registration = registrationRepository
                .findByPersonIdAndSemesterId(personId, semesterId)
                .orElseGet(() -> {
                    SemesterRegistration created = new SemesterRegistration();
                    created.setPersonId(personId);
                    created.setSemesterId(semesterId);
                    created.setStatus(SemesterRegistration.PENDING);
                    return created;
                });
        String from = registration.getAssessment();
        registration.setAssessment(assessment);
        registration.setNote(note);
        registration.setAssessedBy(currentUser.principal().personId());
        registration.setAssessedAt(OffsetDateTime.now());
        registrationRepository.save(registration);
        String membershipFrom = membership.getAssessment();
        membership.setAssessment(assessment);
        membershipRepository.save(membership);
        history(personId, "ASSESSMENT", membershipFrom, assessment, note);
        auditService.record("member.assess", "semester_registration", registration.getId(),
                Map.of("assessment", from), Map.of("assessment", assessment, "semesterId", semesterId), note);
        notificationService.notify(personId, "MEMBER_ASSESSMENT", "学期考核结果",
                "你的考核结论为 " + assessment, "SEMESTER_REGISTRATION", registration.getId());
        return profile(personId, "member:read");
    }

    public List<DisciplineRecord> disciplines(UUID personId) {
        scopeGuard.assertCanAccessPerson(personId, "member:read");
        return disciplineRepository.findByPersonIdOrderByDecidedAtDesc(personId);
    }

    /** 奖惩记录只追加；不触发自动处罚（DOM-12）。 */
    @Transactional
    public DisciplineRecord discipline(UUID personId, String kind, String title, String detail,
                                       OffsetDateTime decidedAt) {
        if (!DisciplineRecord.REWARD.equals(kind) && !DisciplineRecord.PUNISH.equals(kind)) {
            throw ApiException.badRequest("INVALID_DISCIPLINE_KIND", "奖惩类型只能是 REWARD/PUNISH");
        }
        if (title == null || title.isBlank()) {
            throw ApiException.badRequest("TITLE_REQUIRED", "奖惩事项标题不能为空");
        }
        scopeGuard.assertCanAccessPerson(personId, "member:discipline");
        DisciplineRecord record = new DisciplineRecord();
        record.setPersonId(personId);
        record.setKind(kind);
        record.setTitle(title);
        record.setDetail(detail);
        record.setDecidedBy(currentUser.principal().personId());
        record.setDecidedAt(decidedAt == null ? OffsetDateTime.now() : decidedAt);
        disciplineRepository.save(record);
        auditService.record("member.discipline", "discipline_record", record.getId(), null,
                Map.of("kind", kind, "title", title), detail);
        notificationService.notify(personId, "MEMBER_DISCIPLINE", "奖惩记录",
                (DisciplineRecord.REWARD.equals(kind) ? "奖励：" : "处分：") + title,
                "DISCIPLINE", record.getId());
        return record;
    }

    private void history(UUID personId, String dimension, String from, String to, String reason) {
        IdentityHistory entry = new IdentityHistory();
        entry.setPersonId(personId);
        entry.setDimension(dimension);
        entry.setFromValue(from);
        entry.setToValue(to);
        entry.setReason(reason);
        entry.setChangedBy(currentUser.principal().personId());
        historyRepository.save(entry);
    }

    public record MembershipView(String type, String lifecycle, String assessment, OffsetDateTime joinedAt,
                                 OffsetDateTime exitedAt, String exitReason) {
    }

    public record PositionView(UUID id, String positionName, String departmentName, OffsetDateTime startDate,
                               OffsetDateTime endDate, String status) {
    }

    public record MemberProfile(UUID personId, String studentNo, String name, String gender, UUID departmentId,
                                String departmentName, String major, Integer grade, Integer enrollmentYear,
                                String email, String phone, MembershipView membership, List<PositionView> positions) {
    }

    public record MemberSummary(UUID personId, String studentNo, String name, UUID departmentId,
                                String departmentName, String major, Integer grade, String type, String lifecycle,
                                String assessment, OffsetDateTime joinedAt) {
    }
}