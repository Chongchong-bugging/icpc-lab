# 交接简报：B3（队伍 / 任务 / 考勤 / 寒假）

> 给新对话的第一份材料。读完本文件后，仍必须按 AGENTS.md 读 `docs/00-开发边界约束.md`、`docs/plan/PLAN.md`、`docs/plan/TRACEABILITY.md`，并读 [`docs/plan/B3-plan.md`](B3-plan.md)。

## 1. 仓库与分支状态（2026-11-26）

- 仓库：`Chongchong-bugging/icpc-lab`，本地工作目录 `C:\Users\23627\Desktop\UML`。
- `master`：`cb5cc20`（Merge docs: 权限码需同步中文标签 [TECH-09]）。远端仅 `master` 一个分支，无 open PR。
- B2（WP-P0-11..14）已合并：`V4__b2_problem_plan.sql` + `category/`、`problem/`、`importing/`、`trainingplan/`，`mvn verify` 62 测试全绿，权限码 65 个一致，状态 `review` 待 H2.2 人工评审（不阻塞 B3 开发）。
- **B3 需要新分支**：`git switch -c feat/b3-team-task-attendance`（从 `master` 起），完成后开 PR 合并 master。
- 已通过门禁：H1（G0）、H2.0（B0）、H2.1（B1，服务端 34 测试绿，前端页未做）、B2 自检（未开 H2.2 评审会）。
- 已知偏差：本地无 Docker → 集成测试用 H2 2.3.232 PostgreSQL 模式；审计快照 `varchar(4000)` JSON 文本（ADR-0003）；训练计划 `content`/`snapshot` 用 `text` 存 JSON 而非 `jsonb`（H2 绑定不可靠）；`apps/web` 仍是健康检查页，B2 未做前端。

## 2. 本次要交付的四项

| # | WP | 需求 | 交付要点 |
|---|---|---|---|
| 1 | WP-P0-19 | FR-19 / AT-06 / BR-13 | 组队、队长、成员调整、审批、公示；正式队 3 人（`team.xcpc.size` BLOCK）；阵容按时间区间留史（DOM-15）；**补 TEAM 数据范围**（B1 遗留） |
| 2 | WP-P0-15 | FR-10 / FR-11 / AT-03 | 周任务与个人/团队差异化任务；提交→核验；原始/认定分离（DOM-16）；去重计数（DOM-30）；完成率口径（DOM-33） |
| 3 | WP-P0-16 | FR-12 / BR-03 | 训练场次、签到/请假/补录、修订留原因与前后值（DOM-14）；计划时长/签到时段分列（DOM-35）；低出勤**只提醒** |
| 4 | WP-P0-18 | FR-18 / BR-06 | 寒假按周任务（`winter.weeks`=4）、提交/答疑、完成率 |

**本批不做 WP-P0-17 新生培养**（见 §8 第 7 条）：PLAN 把 FR-16 划进 WP-17，但 FR-16 在 TRACEABILITY 是 P1 `deferred`（拔高/普及分组、师徒互选），本批做会越界。若用户要求做，先提计划变更。

详细设计（数据表、接口、错误码、测试、待确认项）见 [`docs/plan/B3-plan.md`](B3-plan.md)。**不要偏离该文件擅改范围**；要改先改计划再改代码。

## 3. 现有代码地图（B2 结束时）

```
apps/api/src/main/java/cn/edu/scuec/icpc/lab/
├── auth/           登录、刷新、登出、JwtService、AuthService
├── security/       PermissionCode(65)、ScopeGuard（仅 SELF/DEPARTMENT/GLOBAL，**无 TEAM**）、JwtAuthenticationFilter、CurrentUser
├── config/         SecurityConfig、SecurityProperties、FileProperties
├── common/         ApiException、GlobalExceptionHandler、PageResponse、BaseEntity、RequestIdFilter
├── identity/       Person、UserAccount、Role、RoleAssignment
├── org/            部门/岗位/届次/学期/任职（OrgService）
├── member/         Membership、IdentityHistory、SemesterRegistration、DisciplineRecord、MemberService、RegistrationService
├── application/    申请 7 态、版本快照、审核轨迹、生效
├── approval/       ApprovalInstance/Node、ApprovalService（N 级、待办、禁自批、permissionFor 按 bizType 映射）
├── notification/   Notification、NotificationService（notify / notifyPermissionHolders / mine / markRead）
├── audit/          AuditService（REQUIRES_NEW、前后快照）
├── file/           FileObject、FileService、FileTokenService（签名下载）
├── rule/           rule_config 维护（BLOCK/WARN/MANUAL），21 个键
├── category/       B2 算法目录（三级、属性、前置关系、停用/合并）
├── problem/        B2 题目与标签（恰一主算法、查重）
├── importing/      B2 CSV 导入框架（PREVIEW→APPLY、错误行回执）
├── trainingplan/   B2 训练计划（版本、提交→审批→发布）
└── seed/           SeedRunner（11 角色/65 权限/21 规则键/参考数据）
```

- 迁移：`db/migration/common/V1__core.sql`（18 表）、`V3__b1_member.sql`、`V4__b2_problem_plan.sql`；PG 专属 `db/migration/postgresql/V2__audit_append_only.sql`，H2 对应 `db/migration/h2/V2__audit_append_only.sql`。**B3 新建 `V5__b3_team_task_attendance.sql`（common 目录）**——当前无 `team`/`task`/`attendance` 表。
- 测试：`apps/api/src/test/java/.../{auth,security,audit,file,seed,application,member,org,category,problem,importing,trainingplan}`，公共支撑 `support/ApiTestSupport`（`mvc`/`objectMapper`/`adminToken()`/`loginToken(user,pwd)`/`createAccount(username,displayName,roleCode,scopeType)`/`newUsername(prefix)`）。
- 分页响应用 `PageResponse{items,page,**size**,total}`——字段名是 `size`，不是 `pageSize`（B2 踩过）。

## 4. 必须遵守的硬约束（摘自总纲，违反即返工）

1. Plan-Execute：每个 WP 先补计划要点，再写代码，再验证，最后回写三份文档（`PLAN.md`、`TRACEABILITY.md`、`WORKLOG.md`）。
2. 每次提交引用条款号，格式 `<type>(<scope>): <summary> [FR-xx|DOM-xx]`。
3. 权限：服务端 `@PreAuthorize` + `ScopeGuard`；**新增权限码五处同步**——`security/PermissionCode.java`、`packages/shared/src/permissions.ts`、**`packages/shared/src/labels.ts`**、`docs/security/权限矩阵.md` §3/§4、`seed/SeedRunner.rolePermissions()`，并跑 `node scripts/check-permissions.mjs`（当前 65）**和** `pnpm -C packages/shared exec tsc --noEmit`（脚本覆盖不到 labels，B2 的 CI 就栽在这里）。B3 预计不新增权限码。
4. 未确认阈值（`absence.consecutive_limit`、`absence.three_times_window_days`、`attendance.min_rate` 目前都是 `MANUAL` 空值）只能提示/人工待办，**不得**自动处罚、自动除名、自动选拔；阈值一律读 `rule_config`，不硬编码。
5. 历史不可改写：审批轨迹、署名、阵容、规则版本保留快照；`team_member` 调队是「结束旧行 + 插新行」，不是 UPDATE 覆盖。
6. 审计：写操作调用 `auditService.record(action, objectType, objectId, before, after, reason)`；不记录凭据/堆栈。考勤修订必须先写 `attendance_amendment` 再改 `attendance_record`。
7. 数据范围进 SQL：非 GLOBAL 用户查询在 SQL 里加范围条件，不能先查全量再过滤。

## 5. 环境与命令（Windows + DSH 沙箱）

```powershell
# 后端编译/测试
$env:MAVEN_ARGS = "-Dmaven.repo.local=C:/Users/23627/Desktop/UML/.cache/m2repo"
mvn -f apps/api/pom.xml verify

# 权限码一致（枚举 / shared / 权限矩阵）
node scripts/check-permissions.mjs

# 只在新增/修改权限码时（B2 的 CI 失败点）
pnpm -C packages/shared exec tsc --noEmit
```

- `git push`、访问 `api.github.com` 在普通沙箱下会失败（`schannel: AcquireCredentialsHandle failed: SEC_E_NO_CREDENTIALS`），需 `danger-full-access`；GCM 有已存凭据时 `git push` 可直接成功。
- 测试库：`@ActiveProfiles("test")` → H2 PG 模式，Flyway 自动应用全部迁移。
- `spring.jpa.hibernate.ddl-auto=none`，Hibernate 不校验列：**实体字段与迁移列必须人工对齐**（B2 的 `category_relation.updated_at` 就是漏列导致全 500）。
- 新增测试请放进对应业务包，命名 `*Test`，用 `ApiTestSupport` 的辅助方法。

## 6. 已踩过的坑（别再踩）

1. **JPA 与 JdbcTemplate 混用要先 flush**：JPA `save` 后用 `JdbcTemplate` 插子表会 FK 失败（B2 `ProblemService.create` 已改 `saveAndFlush`）。
2. **实体继承 `BaseEntity` 就要有 `updated_at`**：迁移漏列 → Hibernate `SQLGrammarException: Column "x.updated_at" not found` → 整个控制器 500。
3. **唯一版本号冲突**：不改内容却写同版本号快照会违反唯一约束（`TrainingPlanService.publish` 因此不写版本）。
4. **事务回滚陷阱**：写操作里复用需要额外权限的查询方法，403 会把整个事务回滚——写完后用自己的视图方法返回。
5. **计数/锁定/失败记录用 `REQUIRES_NEW`**，否则外层异常会回滚计数。
6. **H2 语法限制**：不用 `timestamptz`、不用部分索引、不用 `generated ... stored`、`jsonb` 绑定不可靠；部分唯一性用应用维护的可空键列 + UNIQUE + CHECK（`active_key`、`primary_key` 都是这个套路）。
7. **Jackson `non_null`**：空值字段不出现，测试用 `hasNonNull` 不要 `get(...)`。
8. **审批 ≠ 生效 ≠ 公示**：B3 队伍是「审批通过 → `CONFIRMED`」，公示是另一个动作（写 `published_at` + 通知），测试覆盖两步。
9. **泛型推断**：`List.of(new String[]{...})` 会编译失败，用 `List.<String[]>of(...)`（B2 的 `ProblemTest` 全部改过）。

## 7. 建议实施顺序（每步 `mvn verify`）

1. **WP-19 队伍**：`V5` 建 `team`、`team_member`；`ScopeGuard` 加 `canAccessTeam`/`teamIdsOf`（SQL 层过滤）；`team/` 服务与控制器；`ApprovalService.permissionFor` 加 `TEAM → team:approve`；3 人规则读 `team.xcpc.size`；`TeamTest`。
2. **WP-15 任务**：`task`、`task_problem`、`task_assignment`、`task_completion`；`task/`；完成率与去重口径；`TaskTest`。
3. **WP-16 考勤**：`attendance_session`、`attendance_record`、`attendance_amendment`；`attendance/`；低出勤只生成通知/待办；`AttendanceTest`。
4. **WP-18 寒假**：`task.week_no` + 周模板接口（读 `winter.weeks`）；`WinterTest`。
5. 文档回写：`PLAN.md`（WP-P0-15/16/18/19 → `review`）、`TRACEABILITY.md`（FR-10/11/12/18/19、BR-03/06/13、AT-03/04/06 行 + 新增 §9 B3 条款对照）、`WORKLOG.md`（「2026-xx-xx（B3：…）」）。
6. `mvn verify` 全绿 + 权限一致 → 提交 → 推送分支 → 开 PR → 合并 → 报 H2.3。

## 8. 待确认（先按默认，不阻塞）

1. 队伍按学期（`season_term_id`）建队，跨学期重开新队（默认按 term）。
2. 公示必须在审批通过之后，且为独立动作（默认是）。
3. 「个人与团队不重复计分」口径：同一题在 PERSON 与 TEAM 两个 assignment 都完成时，团队完成率是否也计（默认分列统计，不互相折算）。
4. `WAIVED`/逾期补做是否计入完成率分母（默认新增 `task.count_waived`/`task.count_late` 两个可配键，初始 `WARN` 且计入）。
5. 请假是否走审批流（默认不走，`LEAVE` 只是考勤状态）。
6. 寒假周任务是否自动生成（默认提供模板生成接口，仍需人工触发）。
7. **WP-P0-17 新生培养是否本批不做**（默认不做，FR-16 是 P1 `deferred`；如需做先变更计划并明确 FR-16 归属）。

## 9. 完成定义（DoD）

- 四项功能服务端可用：接口、权限、审计、数据范围齐全。
- 新增测试覆盖门禁：3 人校验与阵容留史（AT-06）、待核实不计分与去重（AT-03）、考勤修订留因与低出勤只提醒（BR-03）、寒假周数与完成率（BR-06）。
- `mvn verify` 全绿（当前 62，预计 ≥90）；权限码仍为 65 或按新增同步为 N 且**五处**一致。
- 三份文档已回写，分支已合并 master，PR 链接与提交哈希可报给用户在 H2.3 评审。