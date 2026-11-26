# B2 计划（算法目录 + 题目与标签 + 训练计划 + 导入框架）

- **版本**：v1.1（2026-11-26，按用户确认纳入 WP-P0-13 导入框架，四项一起交付）
- **依据**：`docs/plan/PLAN.md` §2（B2 批）、`docs/00-开发边界约束.md`、`docs/adr/ADR-0002-domain-model.md`、`docs/security/权限矩阵.md`
- **前置门禁**：H2.1 已确认（B1 状态 `review`，服务端测试 34 绿）；B2 代码在分支 `feat/b2-algorithm-problem-plan` 开发，PR 合并回 master。
- **范式**：Plan-Execute —— 本文件先行，每个 WP 落地前补实现要点，完成后回写 PLAN / TRACEABILITY / WORKLOG。

## 1. 批目标与范围

| WP | 内容 | 需求 | 验收 |
|---|---|---|---|
| WP-P0-11 | 算法目录：三级、属性、停用/合并、前置无环 | FR-06 / FR-37 / FR-38 / AT-13（目录部分） | 三级结构可维护；停用不删除；合并迁移引用；前置成环被拒 |
| WP-P0-12 | 题目与标签：字段、查重、恰一主算法 | FR-07 / FR-40 | 题目 CRUD；重复题拦截；每題恰一主算法 + 若干副标签 |
| WP-P0-13 | 导入框架：成员/考勤/成绩预检、错误行、回执 | NFR-05 | 预检逐行错误；应用导入单事务；回执留存 |
| WP-P0-14 | 训练计划：学期/寒暑假、版本、审批发布、截止校验 | FR-09 / BR-05 | 计划可版本化；审批通过后发布；发布后不可改，只能新版本 |

### 1.1 导入框架（WP-P0-13，NFR-05）

- `import_batch`：`id`、`kind`（MEMBER/ATTENDANCE/RESULT）、`file_object_id`、`status`（PREVIEW/APPLIED/FAILED）、`total/valid/invalid`、`created_by`、时间戳。
- `import_row_error`：`id`、`batch_id`、`row_no`、`column_name`、`code`、`message`、`raw_value`；只追加，不修改历史回执（DOM-15）。
- 预检与应用分离：`preview` 只解析 + 校验并返回逐行错误（HTTP 200 + 报告）；`apply` 在单事务内写入，任一行失败整体回滚。
- 输入为 CSV（UTF-8，MVP），单文件 ≤ 5MB（复用附件上限），行数 ≤ 5000，超出 400 `IMPORT_TOO_LARGE`。
- 不引入第三方 CSV 库：手写 RFC 4180 解析（引号转义、CRLF/LF），避免新增依赖。

**不做**（避免范围外）：OJ 判题、在线提交、题目自动抓取、外部题库同步（DENY/SCOPE 约束）；题目难度自动评分（未确认阈值，按 MANUAL）。

## 2. 领域与数据

### 2.1 目录（FR-06/37/38）

- `algorithm_category` 已存在（code/name/parent_id/sort_order/active），三级由 `parent_id` 自引用表达；层级深度由服务端校验（≤3）。
- 新增 `category_attribute`：`id`、`category_id`、`code`、`name`、`value_type`（TEXT/NUMBER/BOOLEAN）、`required`、`sort_order`、`active`；唯一 `(category_id, code)`。
- 新增 `category_relation`：`id`、`category_id`、`prerequisite_id`、`created_at`；唯一 `(category_id, prerequisite_id)`；自引用禁止、成环拒绝（DFS 校验，服务端）。
- 停用：`active=false`；合并：`merged_into_id` 列（可空，指向保留分类），合并时迁移 `problem_algorithm`/`problem_tag` 引用并置 `active=false`，保留原行（DOM-11）。

### 2.2 题目与标签（FR-07/40）

- `problem`：`id`、`code`（外部题号，可空）、`title`、`source`、`difficulty`（可空，人工）、`statement`（text）、`active`、`created_by/updated_by`、时间戳。
- `problem_algorithm`：`id`、`problem_id`、`category_id`、`is_primary`（布尔）；唯一部分约束用应用维护列模拟：`primary_key = problem_id when is_primary else null`，唯一 `primary_key`。
- `tag`：`id`、`code`、`name`、`active`；`problem_tag`：`(problem_id, tag_id)` 主键。
- 查重：`title` + `source` 归一化后唯一（应用层规范化，DB 唯一索引 `(source_key, title_key)`）。
- 标签为管理员维护字典（DENY-06：不硬编码在代码里，走表 + 种子）。

### 2.3 训练计划（FR-09 / BR-05）

- `training_plan`：`id`、`term_id`、`semester_id`（可空，寒暑假计划用 term）、`kind`（SEMESTER/WINTER/SUMMER）、`title`、`status`（DRAFT/SUBMITTED/APPROVED/PUBLISHED/ARCHIVED）、`current_version`、`published_at`、`published_by`。
- `training_plan_version`：`id`、`plan_id`、`version_no`、`snapshot`（jsonb）、`change_summary`、`created_by`、`created_at`；唯一 `(plan_id, version_no)`。
- 审批复用 `approval_instance`（bizType=`TRAINING_PLAN`），发布后仅允许新版本（DOM-15）。
- 提交截止校验：`plan.submit_deadline_weeks`（已存在，BLOCK）——超过学期开始前 N 周的提交给出阻断提示。

## 3. 权限与接口

- 复用现有权限点：`problem:read/write/import/review`、`category:read/write`、`plan:read/write/approve/publish`。
- 若新增（如 `category:merge`、`tag:write`），必须同时改 `PermissionCode`、`packages/shared/src/permissions.ts`、权限矩阵 §3/§4，并跑 `node scripts/check-permissions.mjs`（默认拒绝 TECH-09/矩阵 §5）。
- 接口草案：
  - `GET/POST /api/v1/algorithm-categories`，`PUT /{id}`，`POST /{id}/merge`，`POST /{id}/attributes`；
  - `GET/POST /api/v1/problems`，`GET/PUT /{id}`，`POST /{id}/algorithms`；
  - `GET/POST /api/v1/training-plans`，`PUT /{id}`，`POST /{id}/submit|approve|publish`，`GET /{id}/versions`。
- 数据范围：题目与目录为实验室级（GLOBAL 或 LAB 级读取）；计划读取按 `plan:read` 范围（SELF/TEAM/DEPARTMENT/GLOBAL 注入查询，TECH-11）。

## 4. 测试与门禁

- `CategoryTest`：三级创建、超深 400、停用后仍可读、合并迁移引用、前置成环 400。
- `ProblemTest`：恰一主算法（0 或 2 个主算法 400 `PRIMARY_ALGORITHM_REQUIRED`/`MULTIPLE_PRIMARY_ALGORITHMS`）、重复题 409、标签绑定。
- `ImportTest`：预检报告行号/列/错误码、错误行不落库、fix 后 apply 成功、超限 400 `IMPORT_TOO_LARGE`、回执可查。
- `TrainingPlanTest`：草稿→提交→审批→发布；发布后修改 409 `PLAN_PUBLISHED_IMMUTABLE`；新版本可建；截止校验。
- 门禁：AT-13（目录/一主算法部分）+ 计划审批发布 + 导入回执；`mvn verify` 全绿；权限码三方一致。

## 5. 待确认（不阻塞开发，先按默认）

1. 算法目录是否严格三级（当前按 ≤3 实现，若必须恰好三级需调整校验）。
2. 题目难度取值（当前自由文本/空，未确认能力评分口径前评估按 MANUAL）。
3. 训练计划发布后是否允许撤回发布（当前按 DOM-15 不可撤回，只能新版本）。
4. 标签是否为全局字典（当前全局 `tag` 表，不分部门）。
5. 导入 CSV 的列定义（成员/考勤/成绩各自模板）——先按 §1.1 自带模板实现，列名固定，后续随业务确认扩展。

## 6. 交付节奏

分支 `feat/b2-algorithm-problem-plan` 按 WP-11 → WP-12 → WP-13 → WP-14 顺序提交，每步 `mvn verify` + 文档回写；全部完成后开 PR 合并 master，报 H2.2 评审。