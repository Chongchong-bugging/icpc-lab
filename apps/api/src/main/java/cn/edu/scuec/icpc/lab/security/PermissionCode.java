package cn.edu.scuec.icpc.lab.security;

import java.util.Arrays;
import java.util.Optional;

/**
 * 权限码权威定义（TECH-09）。前端 packages/shared 为镜像，
 * scripts/check-permissions.mjs 在 CI 中比对两者与权限矩阵文档。
 * 新增权限点必须同时更新：本枚举、shared、docs/security/权限矩阵.md、权限测试。
 */
public enum PermissionCode {

    ACCOUNT_READ("account:read"),
    ACCOUNT_WRITE("account:write"),
    ACCOUNT_RESET("account:reset"),

    ROLE_READ("role:read"),
    ROLE_ASSIGN("role:assign"),

    PROFILE_READ_SELF("profile:read:self"),
    PROFILE_READ_SCOPED("profile:read:scoped"),
    PROFILE_WRITE("profile:write"),

    APPLICATION_CREATE("application:create"),
    APPLICATION_READ_SELF("application:read:self"),
    APPLICATION_READ_SCOPED("application:read:scoped"),
    APPLICATION_REVIEW("application:review"),
    APPLICATION_WITHDRAW("application:withdraw"),

    MEMBER_READ("member:read"),
    MEMBER_WRITE("member:write"),
    MEMBER_ASSESS("member:assess"),
    MEMBER_EXIT("member:exit"),
    MEMBER_DISCIPLINE("member:discipline"),

    ORG_READ("org:read"),
    ORG_WRITE("org:write"),
    POSITION_ASSIGN("position:assign"),

    PROBLEM_READ("problem:read"),
    PROBLEM_WRITE("problem:write"),
    PROBLEM_REVIEW("problem:review"),
    PROBLEM_IMPORT("problem:import"),
    CATEGORY_WRITE("category:write"),

    PLAN_READ("plan:read"),
    PLAN_WRITE("plan:write"),
    PLAN_APPROVE("plan:approve"),
    PLAN_PUBLISH("plan:publish"),

    TASK_READ("task:read"),
    TASK_WRITE("task:write"),
    TASK_ASSIGN("task:assign"),
    TASK_VERIFY("task:verify"),

    ATTENDANCE_READ("attendance:read"),
    ATTENDANCE_WRITE("attendance:write"),
    ATTENDANCE_AMEND("attendance:amend"),

    TEAM_READ("team:read"),
    TEAM_CREATE("team:create"),
    TEAM_APPROVE("team:approve"),
    TEAM_MANAGE("team:manage"),

    CONTEST_READ("contest:read"),
    CONTEST_WRITE("contest:write"),
    REGISTRATION_CREATE("registration:create"),
    REGISTRATION_REVIEW("registration:review"),
    RESULT_WRITE("result:write"),
    RESULT_VERIFY("result:verify"),

    APPROVAL_READ("approval:read"),
    APPROVAL_ACT("approval:act"),
    APPROVAL_DELEGATE("approval:delegate"),

    NOTIFICATION_READ_SELF("notification:read:self"),
    NOTIFICATION_SEND("notification:send"),

    REPORT_READ("report:read"),
    REPORT_READ_SCOPED("report:read:scoped"),

    EXPORT_REQUEST("export:request"),
    EXPORT_APPROVE("export:approve"),
    EXPORT_DOWNLOAD("export:download"),

    RULE_READ("rule:read"),
    RULE_WRITE("rule:write"),

    AUDIT_READ("audit:read"),

    FILE_UPLOAD("file:upload"),
    FILE_DOWNLOAD("file:download"),

    SYSTEM_CONFIG("system:config"),
    JOB_RUN("job:run");

    private final String code;

    PermissionCode(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static Optional<PermissionCode> fromCode(String code) {
        return Arrays.stream(values()).filter(p -> p.code.equals(code)).findFirst();
    }
}