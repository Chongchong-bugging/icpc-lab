# 中南民族大学 ICPC 实验室管理系统

按《ICPC 实验室管理系统需求规格说明书》开发的实验室管理系统（Spring Boot 3.5 + React 19 + PostgreSQL 16）。

**当前进度**：G0 基线、B0 基础设施（迁移 / 认证 / 鉴权 / 审计 / 附件）、B1 组织与会员、B2 题库与训练计划已完成并合并 `master`（后端 `mvn verify` 62 个测试全绿，权限码 65 个）；**B3（队伍 / 任务 / 考勤 / 寒假）尚未开始**，接手请直接读下面的「新同学从这里开始」。

## 新同学从这里开始

1. **读约束**（必读，违反即返工）：[`AGENTS.md`](AGENTS.md) → [`docs/00-开发边界约束.md`](docs/00-开发边界约束.md)
2. **看进度**：[`docs/plan/PLAN.md`](docs/plan/PLAN.md)（批次与门禁）、[`docs/plan/TRACEABILITY.md`](docs/plan/TRACEABILITY.md)（需求 → 代码 → 测试）
3. **接活**：当前批次是 B3，读 [`docs/plan/HANDOFF-B3.md`](docs/plan/HANDOFF-B3.md)（仓库状态、代码地图、硬约束、踩坑、实施顺序）与 [`docs/plan/B3-plan.md`](docs/plan/B3-plan.md)（表结构、接口、错误码、测试、待确认项）
4. **看历史**：[`docs/plan/WORKLOG.md`](docs/plan/WORKLOG.md)（每批做了什么、踩了什么坑）、[`docs/plan/HANDOFF-B2.md`](docs/plan/HANDOFF-B2.md)、[`docs/plan/B1-plan.md`](docs/plan/B1-plan.md)、[`docs/plan/B2-plan.md`](docs/plan/B2-plan.md)

### 文档地图

| 文档 | 用途 |
|---|---|
| [`ICPC实验室管理系统需求规格说明书.docx`](ICPC实验室管理系统需求规格说明书.docx) | 业务事实的唯一权威来源（FR / BR / NFR / AT 编号） |
| [`docs/00-开发边界约束.md`](docs/00-开发边界约束.md) | 总纲：开发范式、范围、技术、领域、数据、质量、流程约束（DOM / DATA / ENG / SCOPE / DENY 条款） |
| [`docs/plan/PLAN.md`](docs/plan/PLAN.md) | 批次 B0..B6、工作包 WP-P0-xx、门禁 H2.x |
| [`docs/plan/TRACEABILITY.md`](docs/plan/TRACEABILITY.md) | 需求 → 工作包 → 代码/测试追溯矩阵 |
| [`docs/plan/WORKLOG.md`](docs/plan/WORKLOG.md) | 逐批工作日志与教训 |
| [`docs/plan/HANDOFF-B3.md`](docs/plan/HANDOFF-B3.md) | **当前批次的交接简报（接手先看这个）** |
| [`docs/plan/B3-plan.md`](docs/plan/B3-plan.md) | 当前批次的详细设计计划 |
| [`docs/adr/`](docs/adr) | 架构决策记录：技术栈、领域模型、审计快照存储 |
| [`docs/security/权限矩阵.md`](docs/security/权限矩阵.md) | 11 角色 × 权限码矩阵（服务端为权威） |
| [`docs/data/种子数据规范.md`](docs/data/种子数据规范.md) | 种子数据与初始角色的约定 |

## 仓库结构

```text
apps/api                 Spring Boot 3.5 + Java 21（Maven）
apps/web                 React 19 + Vite 7 + TypeScript
packages/shared          权限码/角色/中文标签镜像（后端 PermissionCode 为权威）
contract/                OpenAPI 导出（P0 起）
docs/                    总纲、ADR、权限矩阵、种子规范、计划、追溯、交接简报
infra/                   docker-compose（PostgreSQL 16）
scripts/                 Maven 包装、权限码校验、规格导出
```

## 环境

- JDK 21+（`--release 21` 编译）
- Maven 3.9+
- Node 22+，pnpm 11（`corepack enable` 或 `npm i -g pnpm`）
- Docker（可选，仅需要 PostgreSQL 时用 `infra/docker-compose.yml`）

## 本地运行

后端（默认 H2 内存库 + Flyway 自动迁移，无需 Docker）：

```powershell
mvn -f apps/api/pom.xml spring-boot:run
```

前端：

```powershell
pnpm install
pnpm dev:web
```

打开 <http://localhost:5173>，页面会请求 `/api/v1/health` 显示后端状态。

登录账号：开发/测试环境由种子脚本创建 1 个管理员 + 6 个业务角色账号，密码统一 `DevOnly!2026`（首登强制改密，见 [`docs/data/种子数据规范.md`](docs/data/种子数据规范.md)）；生产首次启动必须显式设置 `BOOTSTRAP_ADMIN_PASSWORD`，未设置则 fail fast，不生成默认弱密码。

## 验证（提交前必须全绿）

```powershell
# 后端：编译 + 单元/集成测试（H2 PostgreSQL 模式）
mvn -f apps/api/pom.xml verify

# 权限码一致：后端枚举 / packages/shared / docs/security/权限矩阵.md
node scripts/check-permissions.mjs

# 改过权限码时还要（check-permissions 覆盖不到中文标签）
pnpm -C packages/shared exec tsc --noEmit

# 前端构建
pnpm -r --if-present build
```

> 当前基线：`mvn verify` 62 个测试全绿，权限码 65 个一致。
> 本地无 Docker 时集成测试使用 H2 2.3.232 PostgreSQL 模式；CI 与生产为 PostgreSQL 16。

## 开发流程（不可协商）

1. **Plan-Execute**：先改计划（`docs/plan/Bx-plan.md`）→ 再写代码 → 必须验证 → 必须回写 `PLAN.md` / `TRACEABILITY.md` / `WORKLOG.md`。
2. **一 WP 一分支**：从 `master` 切 `feat/<批次>-<主题>`，完成后开 PR 合并 `master`；每步跑 `mvn verify`。
3. **提交信息引用条款**：`<type>(<scope>): <summary> [FR-xx|DOM-xx]`。
4. **新增权限码五处同步**：`security/PermissionCode.java`、`packages/shared/src/permissions.ts`、`packages/shared/src/labels.ts`、`docs/security/权限矩阵.md` §3/§4、`seed/SeedRunner.rolePermissions()`（漏 `labels.ts` 会让 CI 前端构建失败）。
5. **未确认阈值只能提示**：`absence.*`、`attendance.min_rate`、`score.weights`、`ranking.formula` 等未定项按「未配置 = 人工待办」处理，禁止自动处罚或自动选拔。
6. **历史不可改写**：审批轨迹、署名、阵容、规则版本一律留快照，只追加不覆盖；已产生业务结果的记录不物理删除。

## 当前不做

OJ 评测机、直播、支付、门禁、投票、SSO 直连、原生 App、微服务/K8s/MQ —— 详见总纲 SCOPE-05 与 DENY 清单。

## 待确认问题

Q-01（部门名称）、Q-04/Q-05（阈值）、Q-06（排序公式）、Q-07（暑训日期笔误）等 15 项未定项，系统按「未配置 = 人工待办」处理，不自动处罚或选拔。见总纲 §9。

## 已知偏差

- 本地无 Docker → 集成测试用 H2 PostgreSQL 模式，Testcontainers 待 CI 补。
- 审计前后快照存 `varchar(4000)` JSON 文本（ADR-0003）。
- 训练计划内容 `content` / 版本 `snapshot` 用 `text` 存 JSON（H2 绑定 `jsonb` 不可靠）。
- `apps/web` 目前只有健康检查页，B0..B2 未做业务前端。
- TEAM 数据范围在 B1 时未实现（无队伍模型），B3 的 WP-P0-19 补齐。

> 在 DSH / 受限沙箱内开发时：`git push` 与访问 `api.github.com` 需完整权限；`mvn` 需把本地仓库指向 workspace 内（`$env:MAVEN_ARGS='-Dmaven.repo.local=<repo>/.cache/m2repo'`），否则 `.m2` 写入被拒。普通终端不受此限制。