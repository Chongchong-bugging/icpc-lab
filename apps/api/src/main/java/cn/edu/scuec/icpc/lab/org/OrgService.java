package cn.edu.scuec.icpc.lab.org;

import cn.edu.scuec.icpc.lab.audit.AuditService;
import cn.edu.scuec.icpc.lab.common.ApiException;
import cn.edu.scuec.icpc.lab.identity.AccountService;
import cn.edu.scuec.icpc.lab.identity.PersonRepository;
import cn.edu.scuec.icpc.lab.security.CurrentUser;
import cn.edu.scuec.icpc.lab.security.ScopeGuard;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 组织与任期（FR-01 / AT-02）：任职结束 → 关联授权立即失效，任职行保留。 */
@Service
public class OrgService {

    private final DepartmentRepository departmentRepository;
    private final PositionRepository positionRepository;
    private final TermRepository termRepository;
    private final SemesterRepository semesterRepository;
    private final PositionAssignmentRepository assignmentRepository;
    private final PersonRepository personRepository;
    private final AccountService accountService;
    private final ScopeGuard scopeGuard;
    private final CurrentUser currentUser;
    private final AuditService auditService;

    public OrgService(DepartmentRepository departmentRepository, PositionRepository positionRepository,
                      TermRepository termRepository, SemesterRepository semesterRepository,
                      PositionAssignmentRepository assignmentRepository, PersonRepository personRepository,
                      AccountService accountService, ScopeGuard scopeGuard, CurrentUser currentUser,
                      AuditService auditService) {
        this.departmentRepository = departmentRepository;
        this.positionRepository = positionRepository;
        this.termRepository = termRepository;
        this.semesterRepository = semesterRepository;
        this.assignmentRepository = assignmentRepository;
        this.personRepository = personRepository;
        this.accountService = accountService;
        this.scopeGuard = scopeGuard;
        this.currentUser = currentUser;
        this.auditService = auditService;
    }

    public List<DepartmentView> departments() {
        return departmentRepository.findAllByOrderBySortOrderAsc().stream().map(DepartmentView::of).toList();
    }

    @Transactional
    public DepartmentView createDepartment(CreateDepartment request) {
        scopeGuard.requireGlobal("org:write");
        if (departmentRepository.findByCode(request.code()).isPresent()) {
            throw ApiException.conflict("DEPARTMENT_EXISTS", "部门编码已存在");
        }
        Department department = new Department();
        department.setCode(request.code());
        department.setName(request.name());
        department.setParentId(request.parentId());
        department.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        departmentRepository.save(department);
        auditService.record("org.department.create", "department", department.getId(), null,
                Map.of("code", department.getCode()), "创建部门");
        return DepartmentView.of(department);
    }

    public List<PositionView> positions() {
        return positionRepository.findAllByOrderByLevelDescCodeAsc().stream().map(PositionView::of).toList();
    }

    @Transactional
    public PositionView createPosition(CreatePosition request) {
        scopeGuard.requireGlobal("org:write");
        if (positionRepository.findByCode(request.code()).isPresent()) {
            throw ApiException.conflict("POSITION_EXISTS", "岗位编码已存在");
        }
        Position position = new Position();
        position.setCode(request.code());
        position.setName(request.name());
        position.setLevel(request.level() == null ? 0 : request.level());
        positionRepository.save(position);
        auditService.record("org.position.create", "org_position", position.getId(), null,
                Map.of("code", position.getCode()), "创建岗位");
        return PositionView.of(position);
    }

    public List<TermView> terms() {
        return termRepository.findAllByOrderByCodeDesc().stream().map(TermView::of).toList();
    }

    @Transactional
    public TermView createTerm(CreateTerm request) {
        scopeGuard.requireGlobal("org:write");
        if (termRepository.findByCode(request.code()).isPresent()) {
            throw ApiException.conflict("TERM_EXISTS", "届次编码已存在");
        }
        Term term = new Term();
        term.setCode(request.code());
        term.setName(request.name());
        term.setStartDate(request.startDate());
        term.setEndDate(request.endDate());
        termRepository.save(term);
        auditService.record("org.term.create", "term", term.getId(), null, Map.of("code", term.getCode()), "创建届次");
        return TermView.of(term);
    }

    public List<SemesterView> semesters() {
        return semesterRepository.findAllByOrderBySortOrderAsc().stream().map(SemesterView::of).toList();
    }

    @Transactional
    public SemesterView createSemester(CreateSemester request) {
        scopeGuard.requireGlobal("org:write");
        if (semesterRepository.findByCode(request.code()).isPresent()) {
            throw ApiException.conflict("SEMESTER_EXISTS", "学期编码已存在");
        }
        Semester semester = new Semester();
        semester.setCode(request.code());
        semester.setName(request.name());
        semester.setTermId(request.termId());
        semester.setStartDate(request.startDate());
        semester.setEndDate(request.endDate());
        semester.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        semesterRepository.save(semester);
        auditService.record("org.semester.create", "semester", semester.getId(), null,
                Map.of("code", semester.getCode()), "创建学期");
        return SemesterView.of(semester);
    }

    public List<AssignmentView> assignments(UUID personId) {
        List<PositionAssignment> assignments = personId == null
                ? assignmentRepository.findByStatusOrderByStartDateDesc(PositionAssignment.ACTIVE)
                : assignmentRepository.findByPersonIdOrderByStartDateDesc(personId);
        return assignments.stream().map(AssignmentView::of).toList();
    }

    /**
     * 任职 = 组织岗位 + 可选角色授权。角色授权随任职结束一并回收，权限立即失效（AT-02）。
     */
    @Transactional
    public AssignmentView assign(AssignRequest request) {
        scopeGuard.requireGlobal("position:assign");
        personRepository.findById(request.personId())
                .orElseThrow(() -> ApiException.badRequest("PERSON_NOT_FOUND", "人员不存在"));
        Position position = positionRepository.findByCode(request.positionCode())
                .orElseThrow(() -> ApiException.badRequest("POSITION_NOT_FOUND", "岗位不存在"));
        if (!position.isActive()) {
            throw ApiException.badRequest("POSITION_DISABLED", "岗位已停用");
        }
        LocalDate start = request.startDate() == null ? LocalDate.now() : request.startDate();
        if (request.endDate() != null && request.endDate().isBefore(start)) {
            throw ApiException.badRequest("INVALID_DATES", "结束日期不得早于开始日期");
        }
        String activeKey = request.personId() + ":" + position.getId();
        if (assignmentRepository.findByActiveKey(activeKey).isPresent()) {
            throw ApiException.conflict("ASSIGNMENT_ACTIVE", "该人员在此岗位已有有效任职");
        }
        PositionAssignment assignment = new PositionAssignment();
        assignment.setPersonId(request.personId());
        assignment.setPositionId(position.getId());
        assignment.setDepartmentId(request.departmentId());
        assignment.setTermId(request.termId());
        assignment.setStartDate(start);
        assignment.setEndDate(request.endDate());
        assignment.setActiveKey(activeKey);
        assignment.setReason(request.reason());
        assignment.setGrantedBy(currentUser.principal().personId());
        if (request.roleCode() != null && !request.roleCode().isBlank()) {
            String scopeType = request.scopeType() != null ? request.scopeType()
                    : (request.departmentId() != null ? "DEPARTMENT" : "GLOBAL");
            UUID scopeId = request.departmentId();
            var roleAssignment = accountService.assignInternal(request.personId(), request.roleCode(),
                    scopeType, "DEPARTMENT".equals(scopeType) ? scopeId : null, request.reason());
            assignment.setRoleAssignmentId(roleAssignment.getId());
        }
        assignmentRepository.save(assignment);
        auditService.record("position.assign", "position_assignment", assignment.getId(), null,
                Map.of("personId", request.personId().toString(), "position", position.getCode(),
                        "startDate", start.toString()), request.reason());
        return AssignmentView.of(assignment);
    }

    /** 结束任职：保留历史行，结束关联授权并递增 token_version（到期/离任即时失权）。 */
    @Transactional
    public AssignmentView end(UUID id, String reason) {
        scopeGuard.requireGlobal("position:assign");
        PositionAssignment assignment = assignmentRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("任职记录不存在"));
        if (PositionAssignment.ENDED.equals(assignment.getStatus())) {
            throw ApiException.conflict("ASSIGNMENT_ENDED", "该任职已结束");
        }
        assignment.setStatus(PositionAssignment.ENDED);
        assignment.setActiveKey(null);
        assignment.setEndDate(LocalDate.now());
        assignment.setEndedAt(OffsetDateTime.now());
        assignment.setEndedBy(currentUser.principal().personId());
        assignment.setReason(reason);
        assignmentRepository.save(assignment);
        if (assignment.getRoleAssignmentId() != null) {
            accountService.revokeInternal(assignment.getRoleAssignmentId(), reason == null ? "任职结束" : reason);
        }
        auditService.record("position.end", "position_assignment", id,
                Map.of("status", "ACTIVE"), Map.of("status", "ENDED"), reason);
        return AssignmentView.of(assignment);
    }

    public record CreateDepartment(String code, String name, UUID parentId, Integer sortOrder) {
    }

    public record CreatePosition(String code, String name, Integer level) {
    }

    public record CreateTerm(String code, String name, LocalDate startDate, LocalDate endDate) {
    }

    public record CreateSemester(String code, String name, UUID termId, LocalDate startDate,
                                 LocalDate endDate, Integer sortOrder) {
    }

    public record AssignRequest(UUID personId, String positionCode, UUID departmentId, UUID termId,
                                LocalDate startDate, LocalDate endDate, String roleCode, String scopeType,
                                String reason) {
    }

    public record DepartmentView(UUID id, String code, String name, UUID parentId, int sortOrder, boolean active) {
        static DepartmentView of(Department d) {
            return new DepartmentView(d.getId(), d.getCode(), d.getName(), d.getParentId(), d.getSortOrder(), d.isActive());
        }
    }

    public record PositionView(UUID id, String code, String name, int level, boolean active) {
        static PositionView of(Position p) {
            return new PositionView(p.getId(), p.getCode(), p.getName(), p.getLevel(), p.isActive());
        }
    }

    public record TermView(UUID id, String code, String name, LocalDate startDate, LocalDate endDate, boolean active) {
        static TermView of(Term t) {
            return new TermView(t.getId(), t.getCode(), t.getName(), t.getStartDate(), t.getEndDate(), t.isActive());
        }
    }

    public record SemesterView(UUID id, String code, String name, UUID termId, LocalDate startDate,
                               LocalDate endDate, int sortOrder, boolean active) {
        static SemesterView of(Semester s) {
            return new SemesterView(s.getId(), s.getCode(), s.getName(), s.getTermId(), s.getStartDate(),
                    s.getEndDate(), s.getSortOrder(), s.isActive());
        }
    }

    public record AssignmentView(UUID id, UUID personId, UUID positionId, UUID departmentId, UUID termId,
                                 LocalDate startDate, LocalDate endDate, String status, UUID roleAssignmentId,
                                 String reason, OffsetDateTime endedAt) {
        static AssignmentView of(PositionAssignment a) {
            return new AssignmentView(a.getId(), a.getPersonId(), a.getPositionId(), a.getDepartmentId(),
                    a.getTermId(), a.getStartDate(), a.getEndDate(), a.getStatus(), a.getRoleAssignmentId(),
                    a.getReason(), a.getEndedAt());
        }
    }
}