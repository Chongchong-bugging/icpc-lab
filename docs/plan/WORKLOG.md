# 工作日志（WORKLOG）

> 每次会话结束前追加一条；记录"做了什么/为什么/验证结果/下一步"。不写流水账，写决策与证据。

## 2026-11-26（G0）

**完成**

- 解析需求规格 44 FR / 21 BR / 8 NFR / 14 AT / 15 Q，导出 `scripts/extract_spec.py` 与 `.cache/spec_dump.txt`。
- 范式选型：Plan-Execute；产出 `docs/00-开发边界约束.md` v1.1.0（含 PAR/SCOPE/TECH/DOM/DATA/ENG/DENY 条款）。
- 技术栈改 Java：ADR-0001（Spring Boot 3.5.15 + Java 21 + PostgreSQL 16 + React 19）。
- 领域模型：ADR-0002（身份五维、Q-02 分开建模、Q-03 包含关系、状态机、I-01..I-24）。
- 权限矩阵 v1、种子数据规范 v1、TRACEABILITY v1、PLAN v1。
- 脚手架：`apps/api`（Spring Boot）与 `apps/web`（React+Vite）。

**决策**

- Q-02：新会员默认备选，观察是考核状态（D-01）。
- Q-03：集训队队员 ⊂ 实验室成员，类型层级表达；结束子身份不自动结束父身份（D-02）。
- 本地 Maven 仓库固定 `.cache/m2repo`（默认 `.m2` 在 workspace 外，被沙箱拒绝写入）。
- G0 不实现业务功能；只做可构建、可启动、可测试的基线。

**验证**

- 后端：`mvn test` → `Tests run: 2, Failures: 0, Errors: 0`；`mvn package` 成功；`java -jar apps/api/target/lab-api-0.1.0-SNAPSHOT.jar --server.port=18080` 后 `GET /api/v1/health` 返回 200 且带 `X-Request-Id`，`GET /api/v1/members` 返回 401 `UNAUTHENTICATED`。
- 前端：`pnpm install` + `pnpm -r build` 成功（tsc --noEmit 通过，vite 7.3.8 产出 `apps/web/dist`）。
- 治理：`node scripts/check-permissions.mjs` → `权限码一致：62 个（后端枚举 / shared / 权限矩阵）`。
- 环境限制：DSH 沙箱禁止 Node 派生子进程（`spawn EPERM`），pnpm 的 esbuild postinstall 与脚本需在完整权限下执行；CI 或普通终端不受影响。已在 `pnpm-workspace.yaml` 写入 `allowBuilds: esbuild: true`。
- H1 自检：G0 文档齐备；后端可构建/启动/健康检查；前端可构建；权限码一致；CI 工作流已配置（尚未在 GitHub 实际运行）。H1 为人工门禁，待用户确认后才进入 P0。
- 仓库已 `git init`，尚未提交首个 commit（等待 H1 确认后一并提交基线）。

**下一步**

- 开 WP-P0-02：Flyway V1（人员/账号/角色/组织/申请/会员 + 种子），先落 ADR-0002 中的核心表与不变量。
- 待用户确认：Q-01 部门名称、Q-04/Q-05 阈值、审批链默认级数。

## 2026-11-26（G0 基线归档）

- 远端仓库：<https://github.com/Chongchong-bugging/icpc-lab>（public，默认分支 `master`）。
- 首个 commit `1c13601` 已推送；GitHub Actions 首跑通过（backend / frontend / governance 三个 job）。
- 本地 `.cache/`、`.pnpm-store/`、`node_modules/`、构建产物均按 `.gitignore` 排除，不入库。
## 2026-11-26（B0：基础设施）

**完成**

- WP-P0-02：Flyway V1（`apps/api/src/main/resources/db/migration/common/V1__core.sql`，18 张核心表 + 索引 + CHECK/FK）；PostgreSQL 专属 V2 审计触发器（`db/migration/postgresql/V2__audit_append_only.sql`）；H2 空迁移（`db/migration/h2/V2__audit_append_only.sql`）。
- 种子：`seed/SeedRunner.java`，11 角色 + 角色权限、4 部门、7 岗位、1 届次 + 3 学期、5 身份类型、10 算法大类、21 规则键（5 个 MANUAL 空值）、管理员引导；`seed_run` 记录计数。
- WP-P0-03：`auth/AuthService`、`AuthController`、`LoginAttemptService`、`RefreshCookieFactory`、`JwtService`（HS256，15 分钟 access + 7 天 HttpOnly refresh）、Argon2id（`Argon2PasswordEncoder(16,32,1,19456,2)`）、失败锁定、`token_version` 即时失效。
- WP-P0-04：权限码 62→64（`file:upload` / `file:download`，三方一致）；`AccountPrincipal/ScopeGrant/ScopeType/ScopeGuard/PermissionQuery/JwtAuthenticationFilter`；账号与角色管理 API（`identity/AccountService|RoleService|Controller`）。
- WP-P0-05：`audit/AuditService|AuditController`（REQUIRES_NEW、请求号/IP/UA、前后快照、无写改删端点）；`file/FileService|FileTokenService|FileController`（5MB 上限、SHA-256、PRIVATE/SCOPED/INTERNAL、300 秒签名下载 URL）；规则键维护 `rule/RuleService|RuleController`。
- 文档：PLAN v1.2、TRACEABILITY v1.1、ADR-0003（审计快照存储）、权限矩阵 §3/§4 附件与审批行。

**决策**

- 本地无 Docker/PostgreSQL：集成测试使用 H2 2.3.232 PostgreSQL 模式 + Flyway，SQL 限定双库共同语法（`timestamp with time zone`、无部分索引、无 `generated ... stored`）；Testcontainers 验收留到 CI 有 Docker 时补。
- 部分唯一性用应用维护的可空键列模拟：`membership.active_key`（非终态=person_id）、`membership_application.open_type`（未终结=type），配 UNIQUE + CHECK；PG 侧后续可用部分唯一索引替换。
- 审计快照改 `varchar(4000)` JSON 文本（ADR-0003），生产不可变性由 PG 触发器保证。
- 登录失败计数必须 `REQUIRES_NEW` 独立提交：失败后抛 401 会让外层事务回滚，若同事务计数与锁定会丢失（发现于首轮测试，已修复）。
- `SYSADMIN` 不授予 `application:review / approval:act / approval:delegate / plan:approve / plan:publish / result:verify / team:approve / export:approve`（不得业务审批）。
- 生产管理员密码只来自 `BOOTSTRAP_ADMIN_PASSWORD`，缺失即启动失败；本地 dev 回退 `DevOnly!2026`（仅 default profile）。

**验证**

- `mvn -f apps/api/pom.xml verify` → `Tests run: 24, Failures: 0, Errors: 0`，BUILD SUCCESS。
  - AuthFlowTest 7：登录下发 HttpOnly refresh、/me 权限目录 64、刷新轮换与重放整族撤销、登出撤销、停用后旧 token 立即 401 且再登录 403、5 次失败后 423。
  - AuthorizationTest 4：APPLICANT 读账号/审计/角色 403、读规则 200 而写规则 403、未认证 401、SYSADMIN 正向 200。
  - RoutePermissionTest 2：全路由必须有 `@PublicEndpoint` 或 `@PreAuthorize("hasAuthority(...)")` 且权限码在枚举中；公开路由 ≥5、受控路由 ≥12。
  - AuditAndRuleTest 4：规则更新写审计（含前后快照、requestId）、MANUAL 带值 400、审计无 DELETE 路由、5 个 MANUAL 键保持空值。
  - FileAccessTest 3：签名 URL 无认证下载、篡改 token 401、PRIVATE/SCOPED 越权 403、归档后 404。
  - SeedIdempotencyTest 2：重复执行不新增角色/权限/规则/部门；11 角色、21 规则、5 MANUAL。
  - HealthControllerTest 2：健康检查 200 + 请求号，未认证 401。
- 治理：`node scripts/check-permissions.mjs` → `权限码一致：64 个（后端枚举 / shared / 权限矩阵）`。
- 启动日志：Flyway 应用 2 个迁移；`种子完成：inserted=415 updated=0 skipped=0`。

**遗留 / 下一步**

- 待人工 H2.0 批评审确认 B0 gate（登录/停用即时失效、越权 401/403、审计不可改）。
- 前端登录页与路由守卫未做（B0 只交付服务端契约）；`apps/web` 仍为健康检查页。
- Testcontainers/PostgreSQL 实库验证、`audit_log` jsonb 迁移、TEAM 范围判定（B1 队伍模型落地后补）待补。
- 下一步 B1：FR-01 组织任期、FR-02 入会申请、FR-03 会员档案、FR-04 注册考核、FR-05 退出、FR-29 审批通知。
## 2026-11-26（B1：组织/申请/会员/注册/退出/审批通知）

**完成**

- WP-P0-06：`org/` 部门/岗位/届次/学期/任职（`position_assignment`，active_key 唯一防重复任职）；`OrgService.assign/end` 结束任职即回收角色并递增 `token_version`；页面 `OrgService` 提供只读列表。
- WP-P0-08：`application/` 申请 7 态（DRAFT/SUBMITTED/PROCESSING/RETURNED/APPROVED/REJECTED/WITHDRAWN）+ 版本快照（`application_version`）+ 审核轨迹（`application_review` 只追加）+ `channel`（COMPETITION/GENERAL）；退回可改稿重提（生成新版本）；撤回释放 `open_type`；审批通过 ≠ 生效（`effective_at`/`effected_at` 分离）。
- WP-P0-07：`member/` 会员档案与分类查询（关键词/身份/生命周期/部门）、身份变更留史（`identity_history`）、奖惩记录只追加。
- WP-P0-09：`member/RegistrationService` 学期注册（同人同学期唯一、幂等）、考核三态（PENDING/QUALIFIED/OBSERVING）、观察名单不自动除名（MANUAL 阈值）。
- WP-P0-10：退出/转会生效逻辑：JOIN→LAB_MEMBER/PROBATION；TRANSFER→TEAM_MEMBER；EXIT→EXITED+exitedAt+exitReason；集训队在 `submit` 校验 15 个工作日告知期（不足 409）。
- WP-P0-24：`approval/` 审批实例与节点（多级、待办、撤回）、`notification/` 站内信（有人提交/审核结果/生效结果）；`/approvals/todo` 按业务权限过滤，禁自批。
- 迁移：`db/migration/common/V3__b1_member.sql`（7 张新表 + 申请表补列 `channel/form/effective_at/effected_at`）。
- 文档：PLAN v1.4、TRACEABILITY v1.2（§7 B1 条款对照）、B1-plan v1.0。

**决策**

- 退出告知期只提示、不自动除名：集训队在提交时按 `exit.notice_workdays` 阻断不足天数；普通会员不阻断；`effective_at` 到点后仍需人工 `effect`（D-05 审批≠生效）。
- 审批通过时清空 `open_type`、写入 `effective_at`；`effected_at` 为实际生效时间，两者分离以支撑"已批准未生效"。
- 申请详情与生效操作不共用 `application:review`：生效仅需 `member:write`，避免 SYSADMIN 因不授予业务审批权限而无法完成组织确认（同时避免 `detail()` 的 403 回滚整个事务）。
- 考核结论以 `semester_registration.assessment` 为准并同步 `membership.assessment`，避免两处事实源分叉（ADR-0002）。
- 已退出成员回看档案走"最近一条 membership"（DOM-15 历史可查），不依赖 active_key。

**验证**

- `mvn -f apps/api/pom.xml verify` → `Tests run: 34, Failures: 0, Errors: 0`，BUILD SUCCESS。新增 10 例：
  - `ApplicationFlowTest` 2：JOIN 全流程（退回→v2→重提→通过→生效）、自批 403 且状态不变。
  - `ExitFlowTest` 4：退出全流程（15 个工作日告知期→到期生效→EXITED）、集训队不足 15 日 409、普通会员退出不受告知期限制、非会员 409。
  - `RegistrationTest` 2：注册幂等 + 考核三态同步 + 非法值 400、非会员 409。
  - `OrgAssignmentTest` 2：任职授予角色/重复 409/结束任职旧令牌 401、APPLICANT 403。
- 治理：`node scripts/check-permissions.mjs` → 64 个权限码三方一致。
- Flyway：V1+V2+V3 应用成功；种子仍 415 条。

**遗留 / 下一步**

- 待人工 H2.1 批评审确认 B1 gate：AT-01（申请双路径/学号不重复）、AT-02（任职与任期失效）、AT-10（低出勤不除名 + 退出分流）、退回可重提。服务端已覆盖，浏览器 E2E 待 B2。
- 前端登录页与申请/审批页面未做；`apps/web` 仍是健康检查页。
- 待确认（B1-plan §6）：观察会员口径、考核三态枚举是否最终、普通退出是否需审议会、通知渠道是否仅站内信。
- TEAM 数据范围仍未实现（队伍模型随 B2）；`position_assignment` 的权限到期自动失效目前靠"结束任职"显式操作。



## 2026-11-26（B2：算法目录 + 题目与标签 + 导入框架 + 训练计划）

**完成**

- WP-P0-11 算法目录：`V4__b2_problem_plan.sql` 扩展 `algorithm_category`（`name_key` 唯一、`short_name/description/stage/suggested_difficulty/owner_person_id/merged_into_id`），新增 `category_attribute`、`category_relation`；`category/CategoryService` 提供三级目录、属性 upsert、前置无环（DFS）、停用（有启用子节点拒绝）、同级合并（迁移题目、保留 `merged_into_id` 别名）。
- WP-P0-12 题目与标签：`problem`（`(source_key,title_key)` 唯一）、`problem_algorithm`（`is_primary` + 可空唯一 `primary_key` + check，保证恰一主算法）、`tag`（TYPE/SKILL/YEAR/LEVEL）、`problem_tag`；`problem/ProblemService` 动态过滤（分类/标签/来源/关键词）、分页上限 100、逻辑停用。
- WP-P0-13 导入框架：`import_batch`（PREVIEW/APPLIED/FAILED + `file_sha256`）、`import_row_error`（只追加）；手写 RFC4180 `CsvParser`；`importing/ImportService` 预检→确认两阶段、同文件幂等、错误行 CSV 回执；本期 handler 仅 `MEMBER`（姓名/学号/邮箱/电话/专业/年级）。
- WP-P0-14 训练计划：`training_plan`（kind=学期/寒假/暑假、status 五态、`current_version`、内容 JSON 文本）、`training_plan_version`（版本快照只追加）；`trainingplan/TrainingPlanService` 复用 `ApprovalService`（bizType=`TRAINING_PLAN`，`permissionFor`→`plan:approve`），提交按 `plan.submit_deadline_weeks` 校验，发布后不可改只能 `POST /versions`。
- 权限：新增第 65 个权限码 `import:write`（枚举/shared/权限矩阵/种子 MENTOR 列表四方同步）。

**决策**

- 目录读取复用 `problem:read`、标签字典写入复用 `category:write`，不新增 `category:read`/`tag:write`，避免权限矩阵膨胀与"读题库即读目录"的业务一致性割裂。
- 每题恰一主算法用"可空唯一 `primary_key` + check"在 DB 层兜底（`primary_key = problem_id` 当主算法，否则 NULL），服务层先给 400 语义错误，避免依赖 H2/PG 的表达式索引差异。
- 导入幂等键取 `(kind, file_sha256)` 且只认 PREVIEW/APPLIED 批次：同文件重复提交返回原批次（`duplicate=true`），错误行以 `import_row_error` 为准，不落原始文件（减少个人信息副本）。
- 计划批准后允许编辑：APPROVED 编辑回 DRAFT 并递增版本，避免"批准即冻结"导致学期中计划无法修订；PUBLISHED 才按 DOM-15 永久冻结。
- 属性 code 统一大写存储；前置成环用 409（状态冲突）而非 400，自环仍 400。

**验证**

- `mvn -f apps/api/pom.xml verify` → 62 测试、0 失败 0 错误（B2 新增 28：CategoryTest 8 / ProblemTest 7 / ImportTest 6 / TrainingPlanTest 7）。
- `node scripts/check-permissions.mjs` → 65 个权限码三方一致；`AuthFlowTest.permissionCatalogSize` 同步为 65。
- Flyway 在 H2 上应用 V1..V4；`RoutePermissionTest` 覆盖新增 4 个控制器（均有 `@PreAuthorize`）。

**遗留 / 下一步**

- 待 H2.2 批评审确认 B2 gate（AT-13 服务端部分、计划审批发布、导入回执）；E2E `at-13.spec.ts` 待 P1 前端。
- `ATTENDANCE`/`RESULT` 导入 handler 未开放（返回 400 `IMPORT_KIND_NOT_SUPPORTED`），待考勤/成绩模块（WP-P0-16/19）落地后接入。
- FR-39 学习路线、FR-08 题单、FR-41 题目导入仍 deferred；B2 只提供无环校验与目录/题目基础数据。
- `TEAM` 数据范围、前端题库/计划页面仍未实现。