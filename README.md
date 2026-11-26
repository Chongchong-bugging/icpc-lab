# 中南民族大学 ICPC 实验室管理系统

按《ICPC 实验室管理系统需求规格说明书》开发的实验室管理系统。当前处于 **G0 基线**：文档与脚手架已建立，尚未实现业务功能。

## 先读这三份

1. [`docs/00-开发边界约束.md`](docs/00-开发边界约束.md) —— 范式、范围、技术、领域、数据、质量、流程的总约束
2. [`docs/plan/PLAN.md`](docs/plan/PLAN.md) —— 当前阶段、工作包、门禁
3. [`docs/plan/TRACEABILITY.md`](docs/plan/TRACEABILITY.md) —— 需求 → WP → 代码/测试的追溯

## 仓库结构

```text
apps/api                 Spring Boot 3.5 + Java 21（Maven）
apps/web                 React 19 + Vite 7 + TypeScript
packages/shared          权限码/角色镜像（后端枚举为权威）
contract/                OpenAPI 导出（P0 起）
docs/                    总纲、ADR、权限矩阵、种子规范、计划与追溯
infra/                   docker-compose（PostgreSQL 16）
scripts/                 Maven 包装、权限码校验、规格导出
```

## 环境

- JDK 21+（本机 JDK 25，以 `--release 21` 编译）
- Maven 3.9+
- Node 22+，pnpm 11（本机用内置 pnpm）
- Docker（可选，PostgreSQL）

## 本地运行

后端（默认 H2 内存库，无需 Docker）：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/mvn.ps1 spring-boot:run
```

> `.m2` 默认在 workspace 外会被沙箱拒绝写入，`scripts/mvn.ps1` 已把本地仓库固定到 `.cache/m2repo`。手动执行时需设置：
> `$env:MAVEN_ARGS='-Dmaven.repo.local=C:/Users/23627/Desktop/UML/.cache/m2repo'`

前端：

```powershell
node "C:\Users\23627\.dsh\dsh-runtimes\dsh-primary-runtime\dependencies\pnpm\bin\pnpm.mjs" install
node "C:\Users\23627\.dsh\dsh-runtimes\dsh-primary-runtime\dependencies\pnpm\bin\pnpm.mjs" dev:web
```

打开 <http://localhost:5173>，页面会请求 `/api/v1/health` 显示后端状态。

## 验证

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/mvn.ps1 verify
node scripts/check-permissions.mjs
node "...\pnpm.mjs" -r --if-present build
```

> 本机 DSH 沙箱会阻止 Node 派生子进程（`spawn EPERM`），`pnpm install` 与构建需在完整权限下执行一次；CI 或普通终端不受影响。`esbuild` 构建脚本已通过 `pnpm-workspace.yaml` 的 `allowBuilds` 放行。

## 当前不做

OJ 评测机、直播、支付、门禁、投票、SSO 直连、原生 App、微服务/K8s/MQ —— 详见总纲 SCOPE-05 与 DENY 清单。

## 待确认问题

Q-01（部门名称）、Q-04/Q-05（阈值）、Q-06（排序公式）、Q-07（暑训日期笔误）等 15 项未定项，系统按"未配置 = 人工待办"处理，不自动处罚或选拔。见总纲 §9。