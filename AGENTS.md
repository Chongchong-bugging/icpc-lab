# AGENTS.md

本仓库为「中南民族大学 ICPC 实验室管理系统」。**任何开发动作（人类或 AI）开始前，必须先阅读并遵守：**

- [`docs/00-开发边界约束.md`](docs/00-开发边界约束.md) —— 总约束（范式、范围、技术、领域、数据、质量、流程）
- [`docs/plan/PLAN.md`](docs/plan/PLAN.md) —— 当前计划与工作包状态（G0 已完成）
- [`docs/plan/TRACEABILITY.md`](docs/plan/TRACEABILITY.md) —— 需求 → 代码 → 测试追溯（G0 已建立）

## 不可协商的硬红线（摘要，完整条款以上述总纲为准）

1. 开发范式为 **Plan-Execute**：先计划、再执行、必验证、必回写；不得跳过计划直接改业务代码。
2. 业务事实以《需求规格说明书》为准；工程边界以总纲为准；冲突必须停下并提交变更，不得自行取舍。
3. 未确认阈值（连续不合格、三次缺勤、出勤率 50%、能力评分、排序公式）**只能提示/人工待办**，不得自动处罚或自动选拔。
4. 权限必须在服务端校验；导出单独授权并审计；申请人不得自批。
5. 历史不可改写：审批轨迹、署名、比赛阵容、规则版本一律保留快照。
6. 已产生业务结果的记录不得物理删除；分类被引用只能停用或合并。
7. 不得实现范围外模块（OJ、直播、支付、门禁、投票、原生 App、微服务）。
8. 每项变更必须引用条款号或需求号，并同步更新追溯矩阵。

## 当前状态

- 已完成 G0：总纲 v1.1.0、ADR-0001（Java 技术栈）、ADR-0002（领域模型）、权限矩阵 v1、种子数据规范 v1、TRACEABILITY v1、PLAN v1、WORKLOG、Spring Boot + React 脚手架、CI 骨架。G0 已推送 GitHub（master `e90a4b1`）。
- 已完成 B0（WP-P0-02..05，H2.0 已确认，状态 `verified`）：
  - Flyway `V1__core.sql`（18 表 + 约束/索引）+ PostgreSQL `V2` 审计触发器；`SeedRunner` 幂等种子（11 角色、64 权限映射、21 规则键）。
  - 认证：Argon2id、JWT 访问令牌 15 分钟、refresh 仅 HttpOnly Cookie 且单次轮换、失败锁定、`token_version` 停用即时失效。
  - 鉴权：64 权限码三方一致；`@PreAuthorize` + `ScopeGuard` 数据范围；路由-权限元测试（TECH-10）。
  - 审计：`audit_log` 只追加（PG 触发器 + 无写改删端点）；附件上传/签名下载鉴权（TECH-13）。
- 已完成 B1（WP-P0-06..10/24，状态 `review`，待 H2.1 人工评审）：组织任期（结束任职回收角色）、申请 7 态与版本/审核轨迹、会员档案与身份留史、学期注册与考核三态、退出（集训队 15 个工作日告知期）、审批待办与站内信；迁移 `V3__b1_member.sql`。
- 已验证：`mvn verify` → 34 测试全绿；`node scripts/check-permissions.mjs` → 64 个一致；Flyway 在 H2 上应用 V1+V2+V3；种子 `inserted=415`。
- 门禁：H2.1 批评审确认 B1（AT-01/AT-02/AT-10 + 退回可重提）后开 B2。
- 已知偏差：本地无 Docker，集成测试用 H2 PostgreSQL 模式（Testcontainers 待 CI 补）；审计快照存 `varchar(4000)` JSON 文本（ADR-0003）。


