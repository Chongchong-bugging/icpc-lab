package cn.edu.scuec.icpc.lab.application;

import cn.edu.scuec.icpc.lab.approval.ApprovalInstance;
import cn.edu.scuec.icpc.lab.approval.ApprovalInstanceRepository;
import cn.edu.scuec.icpc.lab.approval.ApprovalService;
import cn.edu.scuec.icpc.lab.audit.AuditService;
import cn.edu.scuec.icpc.lab.common.ApiException;
import cn.edu.scuec.icpc.lab.identity.Person;
import cn.edu.scuec.icpc.lab.identity.PersonRepository;
import cn.edu.scuec.icpc.lab.member.IdentityHistory;
import cn.edu.scuec.icpc.lab.member.IdentityHistoryRepository;
import cn.edu.scuec.icpc.lab.member.Membership;
import cn.edu.scuec.icpc.lab.member.MembershipRepository;
import cn.edu.scuec.icpc.lab.notification.NotificationService;
import cn.edu.scuec.icpc.lab.security.CurrentUser;
import cn.edu.scuec.icpc.lab.security.ScopeGuard;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 入会/转会/退出申请（FR-02/03/05，DOM-07）。
 * 7 态状态机、版本快照、审核轨迹只追加；审批通过 ≠ 生效（D-05），生效单独由管理员确认。
 */
@Service
public class ApplicationService {

    public static final String JOIN = "JOIN";
    public static final String TRANSFER = "TRANSFER";
    public static final String EXIT = "EXIT";

    private final MembershipApplicationRepository applicationRepository;
    private final ApplicationVersionRepository versionRepository;
    private final ApplicationReviewRepository reviewRepository;
    private final ApprovalInstanceRepository instanceRepository;
    private final ApprovalService approvalService;
    private final MembershipRepository membershipRepository;
    private final IdentityHistoryRepository historyRepository;
    private final PersonRepository personRepository;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final CurrentUser currentUser;
    private final ScopeGuard scopeGuard;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbc;

    public ApplicationService(MembershipApplicationRepository applicationRepository,
                              ApplicationVersionRepository versionRepository,
                              ApplicationReviewRepository reviewRepository,
                              ApprovalInstanceRepository instanceRepository,
                              ApprovalService approvalService, MembershipRepository membershipRepository,
                              IdentityHistoryRepository historyRepository, PersonRepository personRepository,
                              NotificationService notificationService, AuditService auditService,
                              CurrentUser currentUser, ScopeGuard scopeGuard, ObjectMapper objectMapper,
                              JdbcTemplate jdbc) {
        this.applicationRepository = applicationRepository;
        this.versionRepository = versionRepository;
        this.reviewRepository = reviewRepository;
        this.instanceRepository = instanceRepository;
        this.approvalService = approvalService;
        this.membershipRepository = membershipRepository;
        this.historyRepository = historyRepository;
        this.personRepository = personRepository;
        this.notificationService = notificationService;
        this.auditService = auditService;
        this.currentUser = currentUser;
        this.scopeGuard = scopeGuard;
        this.objectMapper = objectMapper;
        this.jdbc = jdbc;
    }

    @Transactional
    public ApplicationView create(String type, String channel, String form, String reason) {
        UUID applicant = currentUser.principal().personId();
        if (!JOIN.equals(type) && !TRANSFER.equals(type) && !EXIT.equals(type)) {
            throw ApiException.badRequest("INVALID_APPLICATION_TYPE", "申请类型只能是 JOIN/TRANSFER/EXIT");
        }
        Membership membership = membershipRepository.findByActiveKey(applicant.toString()).orElse(null);
        if (JOIN.equals(type) && membership != null) {
            throw ApiException.conflict("ALREADY_MEMBER", "你已是在册会员，无需再次申请入会");
        }
        if (!JOIN.equals(type) && membership == null) {
            throw ApiException.conflict("NOT_MEMBER", "当前没有在册会员记录，无法发起该申请");
        }
        if (applicationRepository.findByApplicantPersonIdAndOpenType(applicant, type).isPresent()) {
            throw ApiException.conflict("OPEN_APPLICATION_EXISTS", "同类申请尚未终结，不能重复创建");
        }
        boolean approvedNotEffected = applicationRepository.findByApplicantPersonIdOrderByCreatedAtDesc(applicant)
                .stream()
                .anyMatch(existing -> existing.getType().equals(type)
                        && ApplicationStatus.APPROVED.equals(existing.getStatus())
                        && existing.getEffectedAt() == null);
        if (approvedNotEffected) {
            throw ApiException.conflict("APPLICATION_AWAITING_EFFECT", "已有审批通过但尚未生效的同类申请");
        }
        String parsed = parseForm(form);
        MembershipApplication application = new MembershipApplication();
        application.setApplicantPersonId(applicant);
        application.setType(type);
        application.setStatus(ApplicationStatus.DRAFT);
        application.setCurrentVersion(1);
        application.setOpenType(type);
        application.setChannel(channel == null || channel.isBlank() ? "GENERAL" : channel);
        application.setForm(parsed);
        application.setCreatedBy(applicant);
        application.setUpdatedBy(applicant);
        applicationRepository.save(application);
        saveVersion(application, "创建申请", applicant);
        auditService.record("application.create", "membership_application", application.getId(), null,
                Map.of("type", type, "channel", application.getChannel()), reason);
        return detail(application.getId());
    }

    @Transactional
    public ApplicationView update(UUID id, String form, String changeSummary) {
        MembershipApplication application = requireOwned(id, ApplicationStatus.DRAFT, ApplicationStatus.RETURNED);
        application.setForm(parseForm(form));
        application.setCurrentVersion(application.getCurrentVersion() + 1);
        application.setUpdatedBy(currentUser.principal().personId());
        applicationRepository.save(application);
        saveVersion(application, changeSummary == null ? "修改申请" : changeSummary,
                currentUser.principal().personId());
        auditService.record("application.update", "membership_application", application.getId(),
                Map.of("version", application.getCurrentVersion() - 1),
                Map.of("version", application.getCurrentVersion()), changeSummary);
        return detail(application.getId());
    }

    @Transactional
    public ApplicationView submit(UUID id) {
        MembershipApplication application = requireOwned(id, ApplicationStatus.DRAFT, ApplicationStatus.RETURNED);
        if (application.getForm() == null || application.getForm().isBlank()) {
            throw ApiException.badRequest("FORM_REQUIRED", "申请内容不能为空");
        }
        if (ApplicationStatus.RETURNED.equals(application.getStatus())) {
            application.setCurrentVersion(application.getCurrentVersion() + 1);
            saveVersion(application, "退回后重新提交", currentUser.principal().personId());
        }
        validateExitNotice(application);
        application.setStatus(ApplicationStatus.SUBMITTED);
        application.setSubmittedAt(OffsetDateTime.now());
        application.setUpdatedBy(currentUser.principal().personId());
        applicationRepository.save(application);
        String title = titleOf(application);
        approvalService.open(ApprovalService.MEMBERSHIP_APPLICATION, application.getId(), title, 1);
        notificationService.notifyPermissionHolders("application:review", "APPLICATION_SUBMITTED",
                "新的" + typeLabel(application.getType()) + "待审",
                title, "MEMBERSHIP_APPLICATION", application.getId());
        auditService.record("application.submit", "membership_application", application.getId(),
                Map.of("status", ApplicationStatus.DRAFT), Map.of("status", ApplicationStatus.SUBMITTED), null);
        return detail(application.getId());
    }

    @Transactional
    public ApplicationView withdraw(UUID id, String reason) {
        MembershipApplication application = applicationRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("申请不存在"));
        if (ApplicationStatus.terminal(application.getStatus())) {
            throw ApiException.conflict("APPLICATION_TERMINAL", "已终结的申请不可撤回");
        }
        boolean owner = application.getApplicantPersonId().equals(currentUser.principal().personId());
        if (!owner) {
            scopeGuard.assertCanAccessPerson(application.getApplicantPersonId(), "member:write");
        }
        application.setStatus(ApplicationStatus.WITHDRAWN);
        application.setOpenType(null);
        application.setEffectiveAt(null);
        application.setUpdatedBy(currentUser.principal().personId());
        applicationRepository.save(application);
        instanceRepository.findFirstByBizTypeAndBizIdAndStatusOrderByCreatedAtDesc(
                        ApprovalService.MEMBERSHIP_APPLICATION, application.getId(), ApprovalInstance.PENDING)
                .ifPresent(approvalService::cancel);
        auditService.record("application.withdraw", "membership_application", application.getId(),
                Map.of("status", "OPEN"), Map.of("status", ApplicationStatus.WITHDRAWN), reason);
        return detail(application.getId());
    }

    /** 审核：通过/驳回/退回；退回可修改后重提（DOM-07）。 */
    @Transactional
    public ApplicationView review(UUID id, String decision, String comment, String reason) {
        MembershipApplication application = applicationRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("申请不存在"));
        if (!ApplicationStatus.SUBMITTED.equals(application.getStatus())
                && !ApplicationStatus.PROCESSING.equals(application.getStatus())) {
            throw ApiException.conflict("APPLICATION_NOT_REVIEWABLE", "该申请当前状态不可审核");
        }
        scopeGuard.assertCanAccessPerson(application.getApplicantPersonId(), "application:review");
        if (!ApplicationReview.APPROVE.equals(decision) && !ApplicationReview.REJECT.equals(decision)
                && !ApplicationReview.RETURN.equals(decision)) {
            throw ApiException.badRequest("INVALID_DECISION", "审核动作只能是 APPROVE/REJECT/RETURN");
        }
        UUID reviewer = currentUser.principal().personId();
        ApprovalInstance instance = approvalService.requirePending(
                ApprovalService.MEMBERSHIP_APPLICATION, application.getId());
        ApprovalInstance decided = approvalService.decide(instance, reviewer, decision, comment);

        ApplicationReview review = new ApplicationReview();
        review.setApplicationId(application.getId());
        review.setVersionNo(application.getCurrentVersion());
        review.setReviewerPersonId(reviewer);
        review.setDecision(decision);
        review.setComment(comment);
        review.setDecidedAt(OffsetDateTime.now());
        reviewRepository.save(review);

        if (ApplicationReview.APPROVE.equals(decision) && ApprovalInstance.APPROVED.equals(decided.getStatus())) {
            application.setStatus(ApplicationStatus.APPROVED);
            application.setDecidedAt(OffsetDateTime.now());
            application.setEffectiveAt(plannedEffectiveAt(application));
            // APPROVED 属于终态，open_type 必须清空（V1 CHECK）；未生效前的重复申请由 create() 显式拦截
            application.setOpenType(null);
        } else if (ApplicationReview.RETURN.equals(decision)) {
            application.setStatus(ApplicationStatus.RETURNED);
        } else {
            application.setStatus(ApplicationStatus.REJECTED);
            application.setDecidedAt(OffsetDateTime.now());
            application.setOpenType(null);
        }
        application.setUpdatedBy(reviewer);
        applicationRepository.save(application);
        notificationService.notify(application.getApplicantPersonId(), "APPLICATION_REVIEWED",
                "申请审核结果：" + statusLabel(application.getStatus()),
                comment == null ? "" : comment, "MEMBERSHIP_APPLICATION", application.getId());
        auditService.record("application.review", "membership_application", application.getId(),
                Map.of("status", ApplicationStatus.SUBMITTED),
                Map.of("status", application.getStatus(), "decision", decision), reason == null ? comment : reason);
        return detail(application.getId());
    }

    /** 生效：审批通过后由管理员确认；退出受告知期约束，到期前只提示不生效（DOM-07/D-05）。 */
    @Transactional
    public ApplicationView effect(UUID id, String reason) {
        MembershipApplication application = applicationRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("申请不存在"));
        if (!ApplicationStatus.APPROVED.equals(application.getStatus())) {
            throw ApiException.conflict("APPLICATION_NOT_APPROVED", "申请尚未审批通过");
        }
        if (application.getEffectedAt() != null) {
            throw ApiException.conflict("APPLICATION_ALREADY_EFFECTED", "该申请已生效");
        }
        scopeGuard.assertCanAccessPerson(application.getApplicantPersonId(), "member:write");
        OffsetDateTime now = OffsetDateTime.now();
        if (application.getEffectiveAt() != null && application.getEffectiveAt().isAfter(now)) {
            throw ApiException.conflict("NOT_YET_EFFECTIVE",
                    "退出告知期未满，最早生效时间：" + application.getEffectiveAt());
        }
        UUID personId = application.getApplicantPersonId();
        Membership membership = membershipRepository.findByActiveKey(personId.toString()).orElse(null);
        if (EXIT.equals(application.getType())) {
            if (membership == null) {
                throw ApiException.conflict("MEMBERSHIP_NOT_FOUND", "没有可退出的在册会员记录");
            }
            String from = membership.getLifecycle();
            membership.setLifecycle(Membership.EXITED);
            membership.setExitedAt(now);
            membership.setExitReason(exitReason(application, reason));
            membership.setActiveKey(null);
            membershipRepository.save(membership);
            history(personId, "LIFECYCLE", from, Membership.EXITED,
                    "退出申请生效：" + application.getId());
        } else if (JOIN.equals(application.getType())) {
            if (membership != null) {
                throw ApiException.conflict("ALREADY_MEMBER", "该人员已是在册会员");
            }
            Membership created = new Membership();
            created.setPersonId(personId);
            created.setType(Membership.LAB_MEMBER);
            created.setLifecycle(Membership.PROBATION);
            created.setAssessment("PENDING");
            created.setJoinedAt(now);
            created.setActiveKey(personId.toString());
            membershipRepository.save(created);
            history(personId, "LIFECYCLE", null, Membership.PROBATION, "入会申请生效：" + application.getId());
        } else if (TRANSFER.equals(application.getType())) {
            if (membership == null) {
                throw ApiException.conflict("MEMBERSHIP_NOT_FOUND", "没有可转会的在册会员记录");
            }
            String from = membership.getType();
            membership.setType(Membership.TEAM_MEMBER);
            membershipRepository.save(membership);
            history(personId, "CAREER", from, Membership.TEAM_MEMBER, "转会申请生效：" + application.getId());
        }
        application.setEffectedAt(now);
        application.setUpdatedBy(currentUser.principal().personId());
        applicationRepository.save(application);
        notificationService.notify(personId, "APPLICATION_EFFECTED", "申请已生效",
                typeLabel(application.getType()) + "申请已生效", "MEMBERSHIP_APPLICATION", application.getId());
        auditService.record("application.effect", "membership_application", application.getId(),
                Map.of("effected", false), Map.of("effected", true, "type", application.getType()), reason);
        // 生效操作人可能只有 member:write（如 SYSADMIN 不得业务审批），不能再用审批范围复核详情
        return view(application);
    }

    public List<ApplicationView> mine() {
        UUID personId = currentUser.principal().personId();
        return applicationRepository.findByApplicantPersonIdOrderByCreatedAtDesc(personId).stream()
                .map(this::view).toList();
    }

    /** 待办列表：仅返回当前用户数据范围内可见的申请（TECH-11）。 */
    public List<ApplicationView> pending() {
        List<MembershipApplication> candidates = new ArrayList<>();
        candidates.addAll(applicationRepository.findByStatusInOrderBySubmittedAtAsc(
                List.of(ApplicationStatus.SUBMITTED, ApplicationStatus.PROCESSING)));
        return candidates.stream()
                .filter(application -> scopeGuard.canAccessPerson(application.getApplicantPersonId(), "application:review"))
                .map(this::view).toList();
    }

    public ApplicationView detail(UUID id) {
        MembershipApplication application = applicationRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("申请不存在"));
        UUID me = currentUser.principal().personId();
        boolean owner = application.getApplicantPersonId().equals(me);
        if (!owner && !scopeGuard.canAccessPerson(application.getApplicantPersonId(), "application:review")) {
            throw ApiException.forbidden("FORBIDDEN", "无权查看该申请");
        }
        return view(application);
    }

    private ApplicationView view(MembershipApplication application) {
        List<VersionView> versions = versionRepository.findByApplicationIdOrderByVersionNoDesc(application.getId())
                .stream()
                .map(version -> new VersionView(version.getVersionNo(), version.getSnapshot(),
                        version.getChangeSummary(), version.getCreatedAt(), version.getCreatedBy()))
                .toList();
        List<ReviewView> reviews = reviewRepository.findByApplicationIdOrderByDecidedAtAsc(application.getId())
                .stream()
                .map(review -> new ReviewView(review.getVersionNo(), review.getReviewerPersonId(),
                        review.getDecision(), review.getComment(), review.getDecidedAt()))
                .toList();
        UUID instanceId = instanceRepository.findByBizTypeAndBizIdOrderByCreatedAtDesc(
                        ApprovalService.MEMBERSHIP_APPLICATION, application.getId())
                .stream().findFirst().map(ApprovalInstance::getId).orElse(null);
        return new ApplicationView(application.getId(), application.getApplicantPersonId(), application.getType(),
                application.getStatus(), application.getCurrentVersion(), application.getChannel(),
                application.getOpenType(), application.getForm(), application.getSubmittedAt(),
                application.getDecidedAt(), application.getEffectiveAt(), application.getEffectedAt(),
                versions, reviews, instanceId);
    }

    private MembershipApplication requireOwned(UUID id, String... allowedStatuses) {
        MembershipApplication application = applicationRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("申请不存在"));
        if (!application.getApplicantPersonId().equals(currentUser.principal().personId())) {
            throw ApiException.forbidden("FORBIDDEN", "只能修改本人申请");
        }
        for (String allowed : allowedStatuses) {
            if (allowed.equals(application.getStatus())) {
                return application;
            }
        }
        throw ApiException.conflict("APPLICATION_STATE_INVALID", "当前状态不允许该操作：" + application.getStatus());
    }

    private void saveVersion(MembershipApplication application, String changeSummary, UUID actor) {
        ApplicationVersion version = new ApplicationVersion();
        version.setApplicationId(application.getId());
        version.setVersionNo(application.getCurrentVersion());
        version.setSnapshot(snapshotOf(application));
        version.setChangeSummary(changeSummary);
        version.setCreatedBy(actor);
        versionRepository.save(version);
    }

    private String snapshotOf(MembershipApplication application) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "type", application.getType(),
                    "channel", application.getChannel(),
                    "version", application.getCurrentVersion(),
                    "form", application.getForm() == null ? "" : application.getForm()));
        } catch (JsonProcessingException ex) {
            throw ApiException.badRequest("FORM_INVALID", "申请内容无法序列化");
        }
    }

    private String parseForm(String form) {
        if (form == null || form.isBlank()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(objectMapper.readTree(form));
        } catch (JsonProcessingException ex) {
            throw ApiException.badRequest("FORM_INVALID", "申请内容必须是合法 JSON（种子规范/表单契约）");
        }
    }

    private OffsetDateTime plannedEffectiveAt(MembershipApplication application) {
        OffsetDateTime now = OffsetDateTime.now();
        if (!EXIT.equals(application.getType())) {
            return now;
        }
        Integer noticeDays = noticeWorkdays();
        return noticeDays == null || noticeDays <= 0 ? now : now.plus(Duration.ofDays(noticeDays));
    }

    /** BR-04：集训队退出需提前 15 个工作日提出；普通会员退出不阻断（FR-05 两种流程不同）。 */
    private void validateExitNotice(MembershipApplication application) {
        if (!EXIT.equals(application.getType())) {
            return;
        }
        Membership membership = membershipRepository
                .findByActiveKey(application.getApplicantPersonId().toString()).orElse(null);
        if (membership == null || !Membership.TEAM_MEMBER.equals(membership.getType())) {
            return;
        }
        Integer required = noticeWorkdays();
        if (required == null || required <= 0) {
            return;
        }
        LocalDate desired;
        try {
            JsonNode form = objectMapper.readTree(application.getForm());
            String text = form.path("desiredExitDate").asText(null);
            if (text == null || text.isBlank()) {
                return;
            }
            desired = LocalDate.parse(text);
        } catch (JsonProcessingException ex) {
            return;
        } catch (DateTimeParseException ex) {
            throw ApiException.badRequest("INVALID_DESIRED_EXIT_DATE", "期望退出日期格式应为 yyyy-MM-dd");
        }
        long workdays = workdaysBetween(LocalDate.now(), desired);
        if (workdays < required) {
            throw ApiException.conflict("EXIT_NOTICE_TOO_SHORT",
                    "集训队退出需提前 " + required + " 个工作日提出（当前仅 " + workdays + " 个工作日）");
        }
    }

    private Integer noticeWorkdays() {
        List<String> values = jdbc.queryForList(
                "select rule_value from rule_config where rule_key = 'exit.notice_workdays'", String.class);
        return values.isEmpty() || values.get(0) == null ? null : Integer.valueOf(values.get(0));
    }

    private static long workdaysBetween(LocalDate from, LocalDate to) {
        long days = 0;
        for (LocalDate cursor = from.plusDays(1); cursor.isAfter(to) == false; cursor = cursor.plusDays(1)) {
            if (cursor.getDayOfWeek() != DayOfWeek.SATURDAY && cursor.getDayOfWeek() != DayOfWeek.SUNDAY) {
                days++;
            }
        }
        return days;
    }

    private String exitReason(MembershipApplication application, String reason) {
        if (reason != null && !reason.isBlank()) {
            return reason;
        }
        return "退出申请：" + application.getId();
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

    private String titleOf(MembershipApplication application) {
        Person person = personRepository.findById(application.getApplicantPersonId()).orElse(null);
        String name = person == null ? application.getApplicantPersonId().toString() : person.getName();
        return name + " 的" + typeLabel(application.getType());
    }

    private static String typeLabel(String type) {
        return switch (type) {
            case JOIN -> "入会申请";
            case TRANSFER -> "转会申请";
            case EXIT -> "退出申请";
            default -> "申请";
        };
    }

    private static String statusLabel(String status) {
        return switch (status) {
            case ApplicationStatus.APPROVED -> "通过";
            case ApplicationStatus.REJECTED -> "驳回";
            case ApplicationStatus.RETURNED -> "退回修改";
            default -> status;
        };
    }

    public record ApplicationView(UUID id, UUID applicantPersonId, String type, String status, int currentVersion,
                                  String channel, String openType, String form, OffsetDateTime submittedAt,
                                  OffsetDateTime decidedAt, OffsetDateTime effectiveAt, OffsetDateTime effectedAt,
                                  List<VersionView> versions, List<ReviewView> reviews, UUID approvalInstanceId) {
    }

    public record VersionView(int versionNo, String snapshot, String changeSummary, OffsetDateTime createdAt,
                              UUID createdBy) {
    }

    public record ReviewView(int versionNo, UUID reviewerPersonId, String decision, String comment,
                             OffsetDateTime decidedAt) {
    }
}