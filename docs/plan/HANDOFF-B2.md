# 交接简报：B2（算法目录 / 题目与标签 / 训练计划 / 导入框架）

> 给新对话的第一份材料。读完本文件后，仍必须按 AGENTS.md 读 `docs/00-开发边界约束.md`、`docs/plan/PLAN.md`、`docs/plan/TRACEABILITY.md`。

## 1. 仓库与分支状态（2026-11-26）

- 仓库：`Chongchong-bugging/icpc-lab`，本地工作目录 `C:\Users\23627\Desktop\UML`。
- `master`：`5e78c6a`（Merge B2 plan）——包含 B0 `58db461`、B1 `539a97b`、B2 计划 `a180a1c`。
- 分支 `feat/b2-algorithm-problem-plan`：B2 的实现分支，**新功能都提交到这里**，完成后开 PR 合并 master。当前已有 B2 计划文档与本简报的后续修改。
- 已通过门禁：H1（G0）、H2.0（B0）、H2.1（B1，服务端 34 测试绿，前端页未做）；B1 状态 `review` 等人工确认，不阻塞 B2 开发。
- B2 已完成（2026-11-26）：WP-P0-11..14 代码与测试落盘，mvn verify 62 测试全绿、权限码 65 个一致，四项状态 eview，待 H2.2 批评审；实施偏差见 docs/plan/B2-plan.md §7.1。
- 已知偏差：本地无 Docker → 集成测试用 H2 2.3.232 PostgreSQL 模式；审计快照 `varchar(4000)` JSON 文本（ADR-0003）；TEAM 数据范围未实现（B3 队伍模型后补）。

## 2. 本次要交付的四项

| # | WP | 需求 | 交付要点 |
|---|---|---|---|
| 1 | WP-P0-11 | FR-06 / FR-37 / FR-38 | 算法目录三级 ≤3、分类属性、停用/合并（迁移引用、保留历史）、前置关系无环 |
| 2 | WP-P0-12 | FR-07 / FR-40 | 题目 CRUD、查重、**恰一主算法** + 标签、停用不删除 |
| 3 | WP-P0-13 | NFR-05 | CSV 导入框架：PREVIEW 逐行错误 → APPLY 单事务；批量与错误行留回执 |
| 4 | WP-P0-14 | FR-09 / BR-05 | 训练计划版本化、提交→审批→发布、发布后不可改（只能新版本）、截止周校验 |

详细设计见 [`docs/plan/B2-plan.md`](B2-plan.md)（数据表、接口、测试、待确认项）。**不要偏离该文件擅改范围**；要改先改计划再改代码。

## 3. 现有代码地图（B1 结束时）

```
apps/api/src/main/java/cn/edu/scuec/icpc/lab/
├── auth/           登录、刷新、登出、JwtService、AuthService
├── security/       PermissionCode(64)、ScopeGuard、JwtAuthenticationFilter、CurrentUser
├── config/         SecurityConfig、SecurityProperties、FileProperties
├── common/         ApiException、GlobalExceptionHandler、PageResponse、BaseEntity、RequestIdFilter
├── identity/       Person、UserAccount、Role、RoleAssignment、AccountService/RoleService
├── org/            部门/岗位/届次/学期/任职（OrgService）
├── member/         Membership、IdentityHistory、SemesterRegistration、DisciplineRecord、MemberService、RegistrationService
├── application/    申请 7 态、版本快照、审核轨迹、生效（ApplicationService）
├── approval/       ApprovalInstance/Node、ApprovalService（N 级、待办、禁自批）
├── notification/   Notification、NotificationService（站内信）
├── audit/          AuditService（REQUIRES_NEW、前后快照）
├── file/           FileObject、FileService、FileTokenService（签名下载）
├── rule/           rule_config 维护（BLOCK/WARN/MANUAL）
└── seed/           SeedRunner（11 角色/64 权限/21 规则键/参考数据）
```

- 迁移：`apps/api/src/main/resources/db/migration/common/V1__core.sql`（18 表）、`V3__b1_member.sql`；PG 专属 `db/migration/postgresql/V2__audit_append_only.sql`；H2 对应 `db/migration/h2/V2__audit_append_only.sql`。B2 新建 `V4__b2_problem_plan.sql`（common 目录）。
- 测试：`apps/api/src/test/java/.../{auth,security,audit,file,seed,application,member,org}`，公共支撑 `support/ApiTestSupport`（admin 登录、createAccount、loginToken、becomeMember）。

## 4. 必须遵守的硬约束（摘自总纲，违反即返工）

1. Plan-Execute：每个 WP 先补计划要点，再写代码，再验证，最后回写三份文档。
2. 每次提交引用条款号，提交格式 `<type>(<scope>): <summary> [FR-xx|DOM-xx]`。
3. 权限：服务端 `@PreAuthorize` + `ScopeGuard`；新增权限码必须同时改 `PermissionCode.java`、`packages/shared/src/permissions.ts`、`docs/security/权限矩阵.md` §3/§4，并跑 `node scripts/check-permissions.mjs`（当前 64 个）。
4. 未确认阈值只能提示/人工（MANUAL），不得自动处罚/选拔；阈值放 `rule_config`，不硬编码。
5. 历史不可改写：题目/分类/计划只停用或新版本，不物理删除；审核/导入回执只追加。
6. 审计：写操作调用 `auditService.record(action, objectType, objectId, before, after, reason)`；不记录凭据/堆栈。
7. 不外接 OJ、不抓取外部题库、不做判题。

## 5. 环境与命令（Windows + DSH 沙箱）

```powershell
# 后端编译/测试（scripts/mvn.ps1 被执行策略拒绝时用这个）
$env:MAVEN_ARGS = "-Dmaven.repo.local=C:/Users/23627/Desktop/UML/.cache/m2repo"
mvn -f apps/api/pom.xml verify

# 权限码三方一致
node scripts/check-permissions.mjs

# 前端（B2 不要求，如需：沙箱需 danger-full-access）
pnpm install; pnpm build
```

- `git push` 在普通沙箱下会报 `could not read Username`，需 `danger-full-access`。
- 测试库：`@ActiveProfiles("test")` → H2 PG 模式，Flyway 自动应用全部迁移。
- 新增测试请放进对应业务包，命名 `*Test`，用 `ApiTestSupport` 里的辅助方法。

## 6. B0/B1 踩过的坑（别再踩）

1. **事务回滚陷阱**：业务方法里若为了返回视图再调用带 `@PreAuthorize` 的 `detail()`，操作人权限不足会抛 403 并把整个事务回滚（B1 `effect` 已改成 `view(application)`）。写操作结束后返回视图，不要复用需要额外权限的查询方法。
2. **计数/锁定要独立事务**：失败计数、限流等必须 `REQUIRES_NEW`，否则外层抛异常会回滚计数（`LoginAttemptService`）。
3. **H2 语法限制**：不用 `timestamptz`、不用部分索引、不用 `generated ... stored`；部分唯一性用应用维护的可空键列 + UNIQUE + CHECK（`active_key`、`open_type`、`primary_key` 都是这个套路）。
4. **Jackson `non_null`**：空值字段不会出现在响应里，测试用 `hasNonNull` 而不是 `get(...)`。
5. **审批 ≠ 生效**：审核通过只置状态与 `effective_at`，真正落地由有权限的人再确认（`effected_at`），测试要覆盖两步。
6. **数据范围注入查询层**：非 GLOBAL 用户查询一律在 SQL 里加范围条件，不能先查全量再过滤。

## 7. 建议实施顺序

1. **WP-11**：`V4` 建 `category_attribute`、`category_relation`，`algorithm_category` 加 `merged_into_id`；`category/` 服务与控制器；`CategoryTest`。
2. **WP-12**：`problem`、`problem_algorithm`（`primary_key` 模拟唯一）、`tag`、`problem_tag`；`problem/` 服务与控制器；`ProblemTest`。
3. **WP-13**：`import_batch`、`import_row_error`；`importing/` 预检/应用/回执；`ImportTest`。
4. **WP-14**：`training_plan`、`training_plan_version`；复用 `ApprovalService`（bizType=`TRAINING_PLAN`）；`TrainingPlanTest`。
5. 文档回写：`PLAN.md`（WP 状态 → review）、`TRACEABILITY.md`（FR-06/07/09/37/38/40、NFR-05、AT-13 行 + §8 B2 条款对照）、`WORKLOG.md`（「2026-xx-xx（B2：…）」）。
6. `mvn verify` 全绿 + `node scripts/check-permissions.mjs` 64 一致 → 提交 → 推送分支 → 开 PR → 合并 → 报 H2.2。

## 8. 待确认（先按默认，不阻塞）

1. 目录必须严格三级还是 ≤3（当前 ≤3）。
2. 题目难度是否自由文本（当前可空自由文本）。
3. 计划发布后能否撤回（当前不可，只能新版本）。
4. 标签是否全局字典（当前全局）。
5. 导入 CSV 列模板（当前按 B2-plan §1.1 固定列名实现）。

## 9. 完成定义（DoD）

- 四项功能服务端可用：接口、权限、审计、数据范围齐全。
- 新增测试覆盖门禁：三级与成环、恰一主算法、导入错误行、计划发布不可变。
- `mvn verify` 全绿（当前 34，预计 ≥44）；权限码 64 或按新增同步为 N 且三方一致。
- 三份文档已回写，分支已合并 master，PR 链接与提交哈希可报给用户在 H2.2 评审。