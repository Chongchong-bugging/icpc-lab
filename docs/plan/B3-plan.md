# B3 计划（队伍 + 任务 + 考勤 + 寒假）

- **版本**：v1.0（2026-11-26，B3 计划，待新会话执行）
- **依据**：`docs/00-开发边界约束.md`（DOM-12/13/14/15/16/33/35、DATA-01/02/05/06/09、ENG-03/06/07）、`docs/plan/PLAN.md` §3 批次表 B3、`docs/plan/TRACEABILITY.md`。
- **硬约束**：未确认阈值（`absence.*`、`attendance.min_rate`）一律 `MANUAL` → 只提示/人工待办，不阻断、不自动处罚（DOM-12 / DENY-04）。

## 1. 批目标与范围

| # | WP | 需求 | 交付要点 |
|---|---|---|---|
| 1 | WP-P0-19 | FR-19 / AT-06 / BR-13 | 组队、队长、成员调整、审批、公示；正式队 3 人（`team.xcpc.size` BLOCK）；阵容按时间区间留史（DOM-15）；**补 TEAM 数据范围** |
| 2 | WP-P0-15 | FR-10 / FR-11 / AT-03 | 周任务、个人/观察/新生/队伍差异化任务；完成提交与核验；原始/认定分离（DOM-16）；完成率口径可配置（DOM-33） |
| 3 | WP-P0-16 | FR-12 / BR-03 | 训练场次、签到/请假/补录；修订留原因与前后值（DOM-14）；计划时长/签到时段/自报时长分列（DOM-35）；低出勤只提醒 |
| 4 | WP-P0-18 | FR-18 / BR-06 | 寒假按周任务（`winter.weeks`=4）、提交/答疑、完成率 |

**本批不做**：WP-P0-17 新生培养（课程/讲师/材料/师徒/分组）。理由：PLAN 把 FR-16 划进 WP-17，但 FR-16 在 TRACEABILITY 是 **P1 deferred**（拔高/普及分组、师徒互选），B3 做会越界；建议 WP-17 拆为「FR-15 课程与反馈」单独一批，师徒/分组仍留 P1。

## 2. 领域与数据（新迁移 `V5__b3_team_task_attendance.sql`，common 目录）

### 2.1 队伍（FR-19 / BR-13 / DOM-15）

- `team`：`id`、`code`（唯一）、`name`、`season_term_id` FK `term`、`status`（`DRAFT` / `PENDING` / `CONFIRMED` / `REJECTED` / `ARCHIVED`）、`captain_person_id`、`published_at`、`created_by`/`updated_by`、`active`；唯一 `uq_team_season_name(season_term_id, name)`。
- `team_member`：`id`、`team_id`、`person_id`、`role`（`CAPTAIN` / `MEMBER`）、`joined_at`、`left_at`（NULL=在队）、`active_key` 可空唯一（`team_id|person_id`，在队时非空）——沿用 B1 `position_assignment` 的「可空唯一键 + UNIQUE + CHECK」套路（H2 不支持部分索引）。
- **历史阵容不改写**：调队 = 结束旧行（写 `left_at`、清 `active_key`）+ 插入新行，绝不 UPDATE 覆盖已确认阵容；审计 `team.member_add` / `team.member_remove`。
- **3 人规则**：`submit`/`confirm` 时读 `rule_config.team.xcpc.size`（=3，BLOCK），成员数不等于该值 → 409 `TEAM_SIZE_NOT_CONFIRMED`；草稿可不满员（BR-13）。
- **审批**：复用 `ApprovalService`，`bizType=TEAM`；`permissionFor` 增加 `TEAM → team:approve`；提交前 `requirePending`，审批通过 → `CONFIRMED`，退回 → `DRAFT`。审批 ≠ 公示：`publish` 单独接口写 `published_at` 并发通知（DOM-28 同源思路，公示是独立动作）。
- **TEAM 数据范围**（B1 遗留）：`ScopeGuard` 增加 `canAccessTeam(UUID teamId, String permission)` 与 `teamIdsOf(UUID personId)`，非 GLOBAL 用户在 **SQL 层**加 `team_id in (...)` 条件（TECH-11 / DOM-04），不得先查全量再过滤。

### 2.2 任务（FR-10 / FR-11 / DOM-16 / DOM-33）

- `task`：`id`、`plan_id`（FK `training_plan`，可空）、`kind`（`WEEKLY` / `PERSONAL` / `TEAM` / `NEWBIE` / `WINTER`）、`title`、`description`、`target_type`（`PERSON` / `TEAM` / `GROUP`）、`due_at`、`required`、`created_by`/`updated_by`、`active`。
- `task_problem`：`task_id` + `problem_id`（B2 的 `problem`），多算法周任务与「按三级下钻」用它；唯一 `(task_id, problem_id)`。
- `task_assignment`：`id`、`task_id`、`assignee_type`（`PERSON` / `TEAM`）、`assignee_id`、`status`（`ASSIGNED` / `SUBMITTED` / `VERIFIED` / `WAIVED` / `CANCELLED`）、`submitted_at`、`verified_at`、`verified_by`、`note`；唯一 `uq_task_assignee(task_id, assignee_type, assignee_id)`。
- `task_completion`：`id`、`assignment_id`、`problem_id`、`source`（`SELF` / `IMPORT`）、`raw_status`（原始提交）、`verified_status`（认定）、`counted`、`submitted_at`、`verified_at`；唯一 `(assignment_id, problem_id)`——去重计数（DOM-30），原始与认定分列（DOM-16），待核实不计入已确认统计。
- **差异化任务**：`target_type` + `kind` 决定下发对象；个人与团队不重复计分（FR-11）→ 同一 `(task_id, problem_id)` 若既有 PERSON 又有 TEAM assignment，完成率按 `target_type` 口径分别统计，接口返回分列值。
- **完成率**（DOM-33）：`已完成必做 / 应完成必做`；`WAIVED`、`CANCELLED`、逾期补做是否计入由 `rule_config` 决定（新增 `task.count_waived`、`task.count_late`，`WARN`/可配置），分母 0 返回 `null` 并展示「不适用」。

### 2.3 考勤（FR-12 / BR-03 / DOM-14 / DOM-35）

- `attendance_session`：`id`、`plan_id`（可空）、`semester_id`、`title`、`start_at`、`end_at`、`planned_minutes`（计划时长）、`location`、`status`（`SCHEDULED` / `CLOSED`）、`created_by`/`updated_by`。
- `attendance_record`：`id`、`session_id`、`person_id`、`state`（`PRESENT` / `LATE` / `LEAVE` / `ABSENT`）、`checked_in_at`、`checked_minutes`（签到时段）、`source`（`SELF` / `MANUAL` / `IMPORT`）、`note`；唯一 `uq_attendance_record(session_id, person_id)`。
- `attendance_amendment`：`id`、`record_id`、`before_state`、`after_state`、`reason`（非空）、`amended_by`、`amended_at`；**只追加**，修订动作先写 amendment 再更新 record（DOM-14 / DOM-27）。
- **三时长分列**（DOM-35）：`planned_minutes`、`checked_minutes`、自报有效时长，不得以在线时长替代；统计接口分别返回。
- **低出勤只提醒**（BR-03 / DOM-12）：`absence.consecutive_limit`、`absence.three_times_window_days`、`attendance.min_rate` 当前均是 `MANUAL` 空值 → 命中只写 `notification` + 待办（`attendance.review`），**不得**改会员状态、不得除名；规则为 `MANUAL` 时接口返回 `manualPending=true`。

### 2.4 寒假（FR-18 / BR-06）

- 不新建表：复用 `task.kind=WINTER` + `task_assignment`，按 `winter.weeks`（=4）生成周任务；周次由 `week_no` 派生字段或约定 `title` 前缀——**若需要周序号，加 `task.week_no`（可空）**。
- 提交/答疑：`task_assignment.status` 走 `SUBMITTED`，答疑用 `note` + 通知；完成率复用 §2.2 口径。
- `semester.weeks`=12 用于第二学期周任务模板，`training.min_sessions`=8 / `training.min_minutes`=90 用于新生培训统计（本批只统计，不阻断：两者是 `WARN`）。

## 3. 接口（`/api/v1`，复数，ENG-01）

| 资源 | 接口 |
|---|---|
| 队伍 | `GET /teams`（`team:read`，按 term/status/本人）、`GET /teams/{id}`、`POST /teams`（`team:create`）、`PUT /teams/{id}`（`team:manage`）、`POST /teams/{id}/members`、`DELETE /teams/{id}/members/{personId}`（`team:manage`）、`POST /teams/{id}/submit`（`team:create`）、`POST /teams/{id}/approve`（`team:approve`，body `{decision, comment}`）、`POST /teams/{id}/publish`（`team:approve`）、`GET /teams/{id}/members`（含历史区间） |
| 任务 | `GET /tasks`（`task:read`）、`POST /tasks`（`task:write`）、`POST /tasks/{id}/assign`（`task:assign`）、`GET /tasks/{id}/assignments`、`POST /assignments/{id}/submit`、`POST /assignments/{id}/verify`（`task:verify`）、`POST /assignments/{id}/waive`、`GET /tasks/completion-rate`（按对象/周期） |
| 考勤 | `GET /attendance-sessions`（`attendance:read`）、`POST /attendance-sessions`（`attendance:write`）、`POST /attendance-sessions/{id}/records`、`POST /attendance-sessions/{id}/close`、`POST /attendance-records/{id}/amend`（`attendance:amend`，reason 必填）、`GET /attendance-sessions/{id}/summary`（人次/出勤率/待人工审议） |
| 寒假 | `GET /tasks?kind=WINTER&weekNo=`、`POST /tasks/winter-template`（按 `winter.weeks` 生成 4 周任务，`task:write`） |

- 分页统一 `PageResponse{items,page,size,total}`（ENG-03），`size` 上限 100（DATA-09）。
- 错误码集中：`TEAM_NAME_EXISTS`(409)、`TEAM_SIZE_NOT_CONFIRMED`(409)、`TEAM_MEMBER_EXISTS`(409)、`TEAM_CAPTAIN_REQUIRED`(400)、`TEAM_STATUS_INVALID`(409)、`TASK_ASSIGNEE_INVALID`(400)、`ASSIGNMENT_NOT_SUBMITTED`(409)、`ASSIGNMENT_ALREADY_VERIFIED`(409)、`ATTENDANCE_RECORD_EXISTS`(409)、`ATTENDANCE_SESSION_CLOSED`(409)、`ATTENDANCE_AMEND_REASON_REQUIRED`(400)。

## 4. 权限

现有权限码已够用（B3 **预计不新增**，仍为 65）：`team:read/create/approve/manage`、`task:read/write/assign/verify`、`attendance:read/write/amend`。
若确需新增，必须五处同步：`PermissionCode.java`、`packages/shared/src/permissions.ts`、**`packages/shared/src/labels.ts`**（B2 的 CI 失败点）、`docs/security/权限矩阵.md` §3/§4、`SeedRunner.rolePermissions()`，并跑 `node scripts/check-permissions.mjs`。

## 5. 测试与门禁

- `TeamTest`：草稿 2 人可存但提交 409 `TEAM_SIZE_NOT_CONFIRMED`（BR-13）、补齐 3 人提交→审批→确认→公示、调队后历史阵容区间不变（DOM-15）、非本队成员/非队长管理 403（TECH-11）、TEAM 数据范围只返回本队（DOM-04）。
- `TaskTest`：差异化下发（PERSON/TEAM）、提交→核验、原始/认定分离且待核实不计入（DOM-16）、重复提交同一题不重复计数（DOM-30）、`WAIVED` 计入口径随规则变化（DOM-33）、完成率分母 0 返回不适用、MEMBER 写 403。
- `AttendanceTest`：签到幂等（唯一约束）、修订必须写原因并留前后值（DOM-14）、`CLOSED` 场次不可录、低出勤只生成通知不改状态（BR-03，且规则 `MANUAL` 时 `manualPending=true`）、三时长分列（DOM-35）、无 `attendance:write` 403。
- `WinterTest`：按 `winter.weeks` 生成 4 周任务、周序号不重复、完成率与提交/答疑流转（BR-06）。
- 门禁：AT-03（多算法周任务 + 待核实不计分）、AT-04（新生培训次数/时长/周数分列统计）、AT-06（3 人确认与阵容留史）**服务端部分**；`mvn verify` 全绿；权限码数量一致。

## 6. 实施顺序（每步 `mvn verify` + 回写）

1. **WP-19 队伍**：`V5` 建 `team`/`team_member`；`ScopeGuard.canAccessTeam`；`team/` 服务与控制器；`ApprovalService.permissionFor` 加 `TEAM`；`TeamTest`。
2. **WP-15 任务**：`task`/`task_problem`/`task_assignment`/`task_completion`；`task/`；`TaskTest`。
3. **WP-16 考勤**：`attendance_session`/`attendance_record`/`attendance_amendment`；`attendance/`；`AttendanceTest`。
4. **WP-18 寒假**：`task.week_no` + 周模板接口；`WinterTest`。
5. 文档回写：`PLAN.md`（WP-P0-15/16/18/19 → `review`）、`TRACEABILITY.md`（FR-10/11/12/18/19、BR-03/06/13、AT-03/04/06 行 + 新增 §9 B3 条款对照）、`WORKLOG.md`。
6. `mvn verify` 全绿 + 权限一致 → 提交 → 推送分支 → PR → 合并 → 报 H2.3。

## 7. 待确认（先按默认，不阻塞）

1. 队伍是否按学期（`season_term_id`）建队，跨学期是否重开新队（默认按 term）。
2. 队伍公示是否必须审批通过后才可发布（默认是，且发布为独立动作）。
3. 「个人与团队不重复计分」的具体口径：同一题在 PERSON 与 TEAM 两个 assignment 都完成时，团队完成率是否也计（默认分列统计，互不折算）。
4. `WAIVED`/逾期补做是否计入完成率分母（默认新增 `task.count_waived`/`task.count_late` 两个可配键，初始 `WARN` + 计入）。
5. 考勤是否需要「请假」预审批流（默认请假只是 `LEAVE` 状态，不走审批）。
6. 寒假周任务是否需要按周自动生成还是手工新建（默认提供模板生成接口，仍需人工触发）。
7. WP-P0-17 新生培养是否本批不做（默认不做，见 §1）。