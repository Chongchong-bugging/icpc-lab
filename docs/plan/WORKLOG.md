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