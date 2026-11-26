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
