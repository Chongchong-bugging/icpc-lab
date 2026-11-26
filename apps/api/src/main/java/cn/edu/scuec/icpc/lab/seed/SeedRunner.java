package cn.edu.scuec.icpc.lab.seed;

import cn.edu.scuec.icpc.lab.security.PermissionCode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 种子数据（docs/data/种子数据规范.md / WP-P0-02）。
 * - A 类参考数据与 B 类规则键按稳定 code 幂等写入，固定派生 UUID，不物理删除；
 * - 已存在行只更新展示名与说明，不覆盖管理员改过的规则值与状态；
 * - 管理员密码仅来自配置，生产缺失即失败；
 * - 每次执行写入 seed_run 计数。
 */
@Component
public class SeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedRunner.class);
    private static final String SEED_VERSION = "v1";
    private static final String DEV_PASSWORD = "DevOnly!2026";

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final String adminUsername;
    private final String adminPassword;
    private final boolean demoEnabled;

    private int inserted;
    private int updated;
    private int skipped;

    public SeedRunner(JdbcTemplate jdbc, PasswordEncoder passwordEncoder,
                      @Value("${app.bootstrap.admin-username}") String adminUsername,
                      @Value("${app.bootstrap.admin-password}") String adminPassword,
                      @Value("${app.seed.demo:false}") boolean demoEnabled) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
        this.demoEnabled = demoEnabled;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedRoles();
        seedDepartments();
        seedPositions();
        seedTerms();
        seedIdentityTypes();
        seedAlgorithmCategories();
        seedRules();
        bootstrapAdmin();
        if (demoEnabled) {
            seedDemoAccounts();
        }
        jdbc.update("""
                insert into seed_run (id, run_at, seed_version, inserted_count, updated_count, skipped_count, details)
                values (?, current_timestamp, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), SEED_VERSION, inserted, updated, skipped,
                "roles=%d, permissions=%d".formatted(roleCount(), grantedCount()));
        log.info("种子完成：inserted={} updated={} skipped={}", inserted, updated, skipped);
    }

    // ---------------------------------------------------------------- 角色与权限

    private static final Map<String, List<String>> ROLE_PERMISSIONS = rolePermissions();

    private void seedRoles() {
        Map<String, String> roles = new LinkedHashMap<>();
        roles.put("APPLICANT", "申请人");
        roles.put("MEMBER", "实验室成员");
        roles.put("TEAM_MEMBER", "集训队员");
        roles.put("CAPTAIN", "队长");
        roles.put("MENTOR", "导师");
        roles.put("MINISTER", "部长");
        roles.put("PRESIDENT", "会长");
        roles.put("COUNCIL", "理事会");
        roles.put("ADVISOR", "指导老师");
        roles.put("OFFICE", "办公室");
        roles.put("SYSADMIN", "系统管理员");
        roles.forEach((code, name) -> upsertRole(code, name));
        for (Map.Entry<String, List<String>> entry : ROLE_PERMISSIONS.entrySet()) {
            UUID roleId = id("role", entry.getKey());
            for (String permission : entry.getValue()) {
                Integer count = jdbc.queryForObject(
                        "select count(*) from role_permission where role_id = ? and permission_code = ?",
                        Integer.class, roleId, permission);
                if (count != null && count == 0) {
                    jdbc.update("insert into role_permission (role_id, permission_code) values (?, ?)",
                            roleId, permission);
                    inserted++;
                } else {
                    skipped++;
                }
            }
        }
        // 默认拒绝：枚举中的每个权限点都必须被至少一个角色声明，否则视为矩阵漏更新
        Set<String> declared = new LinkedHashSet<>();
        ROLE_PERMISSIONS.values().forEach(declared::addAll);
        for (PermissionCode code : PermissionCode.values()) {
            if (!declared.contains(code.code())) {
                throw new IllegalStateException("权限点未在种子角色映射中声明：" + code.code()
                        + "（权限矩阵 §5 默认拒绝）");
            }
        }
    }

    private void upsertRole(String code, String name) {
        UUID roleId = id("role", code);
        Integer count = jdbc.queryForObject("select count(*) from role where code = ?", Integer.class, code);
        if (count == null || count == 0) {
            jdbc.update("""
                    insert into role (id, code, name, description, builtin, active)
                    values (?, ?, ?, ?, true, true)
                    """, roleId, code, name, name + "（内置角色）");
            inserted++;
        } else {
            jdbc.update("update role set name = ?, description = ? where code = ?",
                    name, name + "（内置角色）", code);
            updated++;
        }
    }

    private static Map<String, List<String>> rolePermissions() {
        Map<String, List<String>> map = new LinkedHashMap<>();
        List<String> all = new ArrayList<>(Arrays.stream(PermissionCode.values()).map(PermissionCode::code).toList());
        List<String> applicant = List.of("profile:read:self", "application:create", "application:read:self",
                "application:withdraw", "org:read", "problem:read", "team:read", "contest:read",
                "notification:read:self", "rule:read", "file:upload", "file:download");
        List<String> member = concat(applicant, "member:read", "plan:read", "task:read", "attendance:read",
                "team:create", "registration:create");
        List<String> captain = concat(member, "profile:read:scoped", "report:read", "report:read:scoped", "team:manage");
        List<String> mentor = List.of("profile:read:self", "profile:read:scoped", "application:create",
                "application:read:self", "application:read:scoped", "application:withdraw", "member:read",
                "member:assess", "problem:read", "problem:write", "problem:import", "plan:read", "plan:write",
                "task:read", "task:write", "task:assign", "task:verify", "attendance:read", "attendance:write",
                "attendance:amend", "team:read", "team:approve", "team:manage", "contest:read",
                "registration:create", "registration:review", "result:write", "approval:read", "approval:act",
                "approval:delegate", "notification:read:self", "notification:send", "report:read",
                "report:read:scoped", "export:request", "rule:read", "file:upload", "file:download", "org:read");
        List<String> minister = concat(mentor, "profile:write", "profile:read:scoped", "member:write", "member:exit",
                "member:discipline", "org:read", "plan:approve", "plan:publish", "contest:write",
                "registration:review", "result:verify", "category:write", "problem:review", "notification:send");
        List<String> president = concat(all.stream()
                .filter(code -> !code.startsWith("account:") && !code.startsWith("role:")
                        && !code.equals("system:config") && !code.equals("job:run") && !code.equals("rule:write"))
                .toList(), "audit:read", "rule:write");
        List<String> council = List.of("profile:read:self", "profile:read:scoped", "application:create",
                "application:read:self", "application:read:scoped", "application:withdraw", "member:read",
                "member:exit", "member:discipline", "org:read", "plan:read", "task:read", "attendance:read",
                "team:read", "contest:read", "report:read", "report:read:scoped", "rule:read",
                "notification:read:self", "file:download");
        List<String> advisor = List.of("profile:read:self", "profile:read:scoped", "application:create",
                "application:read:self", "application:read:scoped", "application:review", "application:withdraw",
                "member:read", "member:assess", "member:exit", "member:discipline", "org:read", "plan:read",
                "plan:approve", "plan:publish", "task:read", "task:verify", "attendance:read", "team:read",
                "team:approve", "contest:read", "registration:review", "result:verify", "approval:read",
                "approval:act", "approval:delegate", "notification:read:self", "notification:send", "report:read",
                "report:read:scoped", "export:request", "export:approve", "export:download", "rule:read",
                "audit:read", "file:download");
        List<String> office = List.of("profile:read:self", "profile:read:scoped", "application:create",
                "application:read:self", "application:read:scoped", "application:review", "application:withdraw",
                "member:read", "org:read", "plan:read", "team:read", "contest:read", "approval:read",
                "approval:act", "approval:delegate", "notification:read:self", "notification:send", "report:read",
                "report:read:scoped", "export:request", "export:download", "rule:read", "file:upload",
                "file:download");
        List<String> sysadmin = concat(all.stream()
                .filter(code -> !code.equals("application:review") && !code.equals("approval:act")
                        && !code.equals("approval:delegate") && !code.equals("plan:approve")
                        && !code.equals("plan:publish") && !code.equals("result:verify")
                        && !code.equals("team:approve") && !code.equals("export:approve"))
                .toList());
        map.put("APPLICANT", applicant);
        map.put("MEMBER", member);
        map.put("TEAM_MEMBER", member);
        map.put("CAPTAIN", captain);
        map.put("MENTOR", mentor);
        map.put("MINISTER", minister);
        map.put("PRESIDENT", president);
        map.put("COUNCIL", council);
        map.put("ADVISOR", advisor);
        map.put("OFFICE", office);
        map.put("SYSADMIN", sysadmin);
        return map;
    }

    private static List<String> concat(List<String> base, String... extra) {
        List<String> result = new ArrayList<>(base);
        for (String value : extra) {
            if (!result.contains(value)) {
                result.add(value);
            }
        }
        return List.copyOf(result);
    }

    private int roleCount() {
        Integer count = jdbc.queryForObject("select count(*) from role", Integer.class);
        return count == null ? 0 : count;
    }

    private int grantedCount() {
        Integer count = jdbc.queryForObject("select count(*) from role_permission", Integer.class);
        return count == null ? 0 : count;
    }

    // ---------------------------------------------------------------- 组织参考数据

    private void seedDepartments() {
        seedDepartment("COMPETITION", "竞赛部", 10);
        seedDepartment("OFFICE", "办公室", 20);
        seedDepartment("PROPAGANDA", "宣传部", 30);
        seedDepartment("ORGANIZATION", "组织部", 40);
    }

    private void seedDepartment(String code, String name, int sort) {
        UUID departmentId = id("department", code);
        Integer count = jdbc.queryForObject("select count(*) from department where code = ?", Integer.class, code);
        if (count == null || count == 0) {
            jdbc.update("""
                    insert into department (id, code, name, parent_id, sort_order, active)
                    values (?, ?, ?, null, ?, true)
                    """, departmentId, code, name, sort);
            inserted++;
        } else {
            jdbc.update("update department set name = ?, sort_order = ? where code = ?", name, sort, code);
            updated++;
        }
    }

    private void seedPositions() {
        seedPosition("MINISTER", "部长", 90);
        seedPosition("VICE_MINISTER", "副部长", 80);
        seedPosition("PRESIDENT", "会长", 100);
        seedPosition("VICE_PRESIDENT", "副会长", 90);
        seedPosition("COUNCIL_MEMBER", "理事会成员", 70);
        seedPosition("MENTOR", "导师", 60);
        seedPosition("ASSISTANT_COACH", "助理教练", 50);
    }

    private void seedPosition(String code, String name, int level) {
        UUID positionId = id("org_position", code);
        Integer count = jdbc.queryForObject("select count(*) from org_position where code = ?", Integer.class, code);
        if (count == null || count == 0) {
            jdbc.update("""
                    insert into org_position (id, code, name, level, active) values (?, ?, ?, ?, true)
                    """, positionId, code, name, level);
            inserted++;
        } else {
            jdbc.update("update org_position set name = ?, level = ? where code = ?", name, level, code);
            updated++;
        }
    }

    private void seedTerms() {
        UUID termId = id("term", "2026");
        Integer count = jdbc.queryForObject("select count(*) from term where code = ?", Integer.class, "2026");
        if (count == null || count == 0) {
            jdbc.update("insert into term (id, code, name, active) values (?, ?, ?, true)",
                    termId, "2026", "2026 届");
            inserted++;
        }
        seedSemester("2026-2027-1", "2026-2027 学年第一学期", 10);
        seedSemester("2026-2027-2", "2026-2027 学年第二学期", 20);
        seedSemester("2026-2027-SUMMER", "2026-2027 学年暑期学期", 30);
    }

    private void seedSemester(String code, String name, int sort) {
        Integer count = jdbc.queryForObject("select count(*) from semester where code = ?", Integer.class, code);
        if (count == null || count == 0) {
            jdbc.update("""
                    insert into semester (id, term_id, code, name, active, sort_order)
                    values (?, ?, ?, ?, true, ?)
                    """, id("semester", code), id("term", "2026"), code, name, sort);
            inserted++;
        } else {
            jdbc.update("update semester set name = ?, sort_order = ? where code = ?", name, sort, code);
            updated++;
        }
    }

    private void seedIdentityTypes() {
        seedIdentityType("LAB_MEMBER", "实验室成员", null, 10);
        seedIdentityType("TEAM_MEMBER", "集训队员", "LAB_MEMBER", 20);
        seedIdentityType("FRESHMAN", "新生", null, 30);
        seedIdentityType("MENTOR", "导师", null, 40);
        seedIdentityType("ASSISTANT_COACH", "助理教练", null, 50);
    }

    private void seedIdentityType(String code, String name, String parentCode, int sort) {
        UUID identityId = id("identity_type", code);
        Integer count = jdbc.queryForObject("select count(*) from identity_type where code = ?", Integer.class, code);
        if (count == null || count == 0) {
            UUID parent = parentCode == null ? null : id("identity_type", parentCode);
            jdbc.update("""
                    insert into identity_type (id, code, name, parent_id, sort_order, active)
                    values (?, ?, ?, ?, ?, true)
                    """, identityId, code, name, parent, sort);
            inserted++;
        } else {
            jdbc.update("update identity_type set name = ?, sort_order = ? where code = ?", name, sort, code);
            updated++;
        }
    }

    private void seedAlgorithmCategories() {
        seedAlgorithmCategory("BASIC", "基础算法", 10);
        seedAlgorithmCategory("DATA_STRUCTURE", "数据结构", 20);
        seedAlgorithmCategory("GRAPH", "图论", 30);
        seedAlgorithmCategory("DP", "动态规划", 40);
        seedAlgorithmCategory("MATH", "数学", 50);
        seedAlgorithmCategory("STRING", "字符串", 60);
        seedAlgorithmCategory("GEOMETRY", "计算几何", 70);
        seedAlgorithmCategory("SEARCH", "搜索", 80);
        seedAlgorithmCategory("GREEDY", "贪心", 90);
        seedAlgorithmCategory("SIMULATION", "模拟与构造", 100);
    }

    private void seedAlgorithmCategory(String code, String name, int sort) {
        Integer count = jdbc.queryForObject("select count(*) from algorithm_category where code = ?",
                Integer.class, code);
        if (count == null || count == 0) {
            jdbc.update("""
                    insert into algorithm_category (id, code, name, parent_id, sort_order, active)
                    values (?, ?, ?, null, ?, true)
                    """, id("algorithm_category", code), code, name, sort);
            inserted++;
        } else {
            jdbc.update("update algorithm_category set name = ?, sort_order = ? where code = ?", name, sort, code);
            updated++;
        }
    }

    // ---------------------------------------------------------------- 规则键

    private void seedRules() {
        seedRule("mentor.max_trainees", "3", "BLOCK", "每位导师最多带训人数", "DOM-12 / R-03");
        seedRule("group.popular.min", "5", "WARN", "热门分组最少人数", "DOM-12 / R-05");
        seedRule("group.popular.max", "7", "WARN", "热门分组最多人数", "DOM-12 / R-05");
        seedRule("group.advanced.target", "15", "WARN", "提高组目标人数", "DOM-12 / R-06");
        seedRule("team.xcpc.size", "3", "BLOCK", "XCPC 队伍人数", "DOM-12 / R-09");
        seedRule("training.min_sessions", "8", "WARN", "每学期最低训练次数", "DOM-12 / R-12");
        seedRule("training.min_minutes", "90", "WARN", "单次训练最低时长（分钟）", "DOM-12 / R-12");
        seedRule("winter.weeks", "4", "WARN", "寒假集训周数", "DOM-12 / R-14");
        seedRule("semester.weeks", "12", "WARN", "学期训练周数", "DOM-12 / R-14");
        seedRule("plan.submit_deadline_weeks", "2", "BLOCK", "计划提前提交周数", "DOM-12 / R-16");
        seedRule("feedback.workdays", "3", "WARN", "反馈处理工作日", "DOM-12 / R-18");
        seedRule("selection.summary_workdays", "3", "WARN", "选拔结果汇总工作日", "DOM-12 / R-19");
        seedRule("exit.notice_workdays", "15", "BLOCK", "退会提前申请工作日", "DOM-12 / R-21");
        seedRule("sim.weekly_min", "3", "WARN", "每周最少模拟赛次数", "DOM-12 / R-24");
        seedRule("absence.consecutive_limit", null, "MANUAL", "连续缺席上限（未确认）", "DOM-12 / DENY-04");
        seedRule("absence.three_times_window_days", null, "MANUAL", "三次缺勤统计窗口（未确认）", "DOM-12 / DENY-04");
        seedRule("attendance.min_rate", null, "MANUAL", "最低出勤率（未确认）", "DOM-12 / DENY-04");
        seedRule("score.weights", null, "MANUAL", "能力评分权重（未确认）", "DOM-12 / DENY-04");
        seedRule("ranking.formula", null, "MANUAL", "排序公式（未确认）", "DOM-12 / DENY-04");
        seedRule("finance.dual_approval_threshold", "500", "BLOCK", "双审批金额阈值", "DOM-12 / R-27");
        seedRule("finance.publicity_workdays", "3", "WARN", "财务公示工作日", "DOM-12 / R-27");
    }

    private void seedRule(String key, String value, String mode, String description, String clause) {
        Integer count = jdbc.queryForObject("select count(*) from rule_config where rule_key = ?",
                Integer.class, key);
        if (count == null || count == 0) {
            jdbc.update("""
                    insert into rule_config (id, rule_key, rule_value, mode, description, version, source_clause)
                    values (?, ?, ?, ?, ?, 1, ?)
                    """, id("rule_config", key), key, value, mode, description, clause);
            inserted++;
        } else {
            // 只同步说明与条款号，管理员改过的值与模式不被覆盖
            jdbc.update("update rule_config set description = ?, source_clause = ? where rule_key = ?",
                    description, clause, key);
            updated++;
        }
    }

    // ---------------------------------------------------------------- 账号引导

    private void bootstrapAdmin() {
        Integer count = jdbc.queryForObject("select count(*) from user_account where username = ?",
                Integer.class, adminUsername);
        if (count != null && count > 0) {
            skipped++;
            return;
        }
        if (adminPassword == null || adminPassword.isBlank()) {
            throw new IllegalStateException(
                    "缺少 BOOTSTRAP_ADMIN_PASSWORD：生产环境必须显式提供管理员初始密码（种子规范 §3）");
        }
        UUID personId = id("person", "bootstrap-admin");
        UUID accountId = id("user_account", adminUsername);
        jdbc.update("""
                insert into person (id, name, gender, remark)
                values (?, ?, 'UNKNOWN', '系统引导账号')
                """, personId, "系统管理员");
        jdbc.update("""
                insert into user_account (id, person_id, username, password_hash, status, must_change_password)
                values (?, ?, ?, ?, 'ACTIVE', true)
                """, accountId, personId, adminUsername, passwordEncoder.encode(adminPassword));
        insertAssignment(personId, "SYSADMIN", "GLOBAL", null, accountId);
        inserted += 2;
        log.info("已引导管理员账号 {}", adminUsername);
    }

    private void seedDemoAccounts() {
        seedDemoAccount("demo.mentor", "演示导师", "MENTOR", "GLOBAL");
        seedDemoAccount("demo.member", "演示成员", "MEMBER", "SELF");
        seedDemoAccount("demo.applicant", "演示申请人", "APPLICANT", "SELF");
    }

    private void seedDemoAccount(String username, String name, String roleCode, String scopeType) {
        Integer count = jdbc.queryForObject("select count(*) from user_account where username = ?",
                Integer.class, username);
        if (count != null && count > 0) {
            skipped++;
            return;
        }
        UUID personId = id("person", username);
        jdbc.update("insert into person (id, name, gender, remark) values (?, ?, 'UNKNOWN', '演示账号')",
                personId, name);
        jdbc.update("""
                insert into user_account (id, person_id, username, password_hash, status, must_change_password)
                values (?, ?, ?, ?, 'ACTIVE', false)
                """, id("user_account", username), personId, username, passwordEncoder.encode(DEV_PASSWORD));
        insertAssignment(personId, roleCode, scopeType, null, null);
        inserted += 2;
    }

    private void insertAssignment(UUID personId, String roleCode, String scopeType, UUID scopeId, UUID grantedBy) {
        jdbc.update("""
                insert into role_assignment (id, person_id, role_id, scope_type, scope_id, valid_from, granted_by, reason)
                values (?, ?, ?, ?, ?, current_timestamp, ?, ?)
                """, id("role_assignment", personId + ":" + roleCode + ":" + scopeType), personId,
                id("role", roleCode), scopeType, scopeId, grantedBy, "种子引导");
    }

    // ---------------------------------------------------------------- 工具

    private static UUID id(String kind, String code) {
        return UUID.nameUUIDFromBytes(("icpc-lab:" + kind + ":" + code).getBytes(StandardCharsets.UTF_8));
    }
}